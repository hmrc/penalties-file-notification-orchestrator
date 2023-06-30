/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import java.time.LocalDateTime

import base.SpecBase
import models.FailedJobResponses.FailedToProcessNotifications
import models.notification._
import models.{MongoLockResponses, SDESNotificationRecord}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually.eventually
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.FileNotificationRepository
import scheduler.ScheduleStatus
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys
import utils.{LogCapturing, TimeMachine}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class NotProcessedFilesServiceSpec extends SpecBase with LogCapturing {
  val mockLockRepository: MongoLockRepository = mock(classOf[MongoLockRepository])
  val mockConfig: Configuration = mock(classOf[Configuration])
  val mockTimeMachine: TimeMachine = mock(classOf[TimeMachine])
  val mockFileNotificationRepository: FileNotificationRepository = mock(classOf[FileNotificationRepository])
  val jobName = "NotProcessedFilesService"

  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds

  val mockDateTime: LocalDateTime = LocalDateTime.of(2022, 1, 1, 0, 0, 0)
  val notification: SDESNotification = SDESNotification(
    informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "file1.txt",
      location = "http://example.com",
      checksum = SDESChecksum(algorithm = "SHA-256", value = "123456789-abcdef-123456789"),
      size = 256,
      properties = Seq.empty[SDESProperties]
    ),
    audit = SDESAudit("file 1")
  )


  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref1",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = mockDateTime.minusHours(1),
    updatedAt = mockDateTime,
    nextAttemptAt = mockDateTime,
    notification = notification
  )

  val pendingNotifications: Seq[SDESNotificationRecord] = Seq(
    notificationRecord,
    notificationRecord.copy(reference= "ref2", updatedAt = mockDateTime.minusSeconds(1), status = RecordStatusEnum.FILE_RECEIVED_IN_SDES),
    notificationRecord.copy(reference = "ref3", updatedAt = mockDateTime.plusHours(1), status = RecordStatusEnum.FILE_RECEIVED_IN_SDES)
  )

  class Setup(withMongoLockStubs: Boolean = true) {
    reset(mockLockRepository, mockConfig, mockFileNotificationRepository, mockTimeMachine)
    val service = new NotProcessedFilesService(mockLockRepository, mockFileNotificationRepository, mockTimeMachine, mockConfig, appConfig)
    when(mockConfig.get[Int](Matchers.eq(s"schedules.${service.jobName}.mongoLockTimeout"))(Matchers.any()))
      .thenReturn(mongoLockTimeout)
    when(mockTimeMachine.now).thenReturn(mockDateTime.plusMinutes(appConfig.configurableTimeMinutes))
    if (withMongoLockStubs) {
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.successful(()))
    }
  }

  "invoke" should {
    "run the job successfully if there are no relevant notifications" in new Setup {
      when(mockFileNotificationRepository.getFilesReceivedBySDES()).thenReturn(Future.successful(Seq.empty))
      val result: Either[ScheduleStatus.JobFailed, String] = await(service.invoke)
      result.isRight shouldBe true
      result.getOrElse("fail") shouldBe "Processed all notifications"
    }

    "process the notifications and return Right if they all succeed - only process if nextAttemptAt + 60 minutes < now" in new Setup {
      when(mockFileNotificationRepository.getFilesReceivedBySDES()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.any(), Matchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(reference = "ref2", status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY, updatedAt = LocalDateTime.now())
      ))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.getOrElse("fail") shouldBe "Processed all notifications"
      verify(mockFileNotificationRepository, times(1)).updateFileNotification(Matchers.eq("ref2"), Matchers.eq(RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY))
    }

    "process the notifications and return Left if some fail" in new Setup {
      val exception = new Exception("woopsy")
      when(mockFileNotificationRepository.getFilesReceivedBySDES()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(exception))
        .thenReturn(Future.successful(notificationRecord.copy(reference = "ref2", status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY, updatedAt = LocalDateTime.now())))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.getOrElse("fail") shouldBe FailedToProcessNotifications
      withCaptureOfLoggingFrom(logger) {
        logs => {
          eventually {
            logs.exists(_.getMessage.contains(PagerDutyKeys.UNKNOWN_PROCESSING_EXCEPTION)) shouldBe true
            logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION)) shouldBe true
          }
        }
      }
    }

    "process the notifications and return Left if all fail" in new Setup {
      val exception = new Exception("woopsy")
      when(mockFileNotificationRepository.getFilesReceivedBySDES()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(exception))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.getOrElse("fail") shouldBe FailedToProcessNotifications
      withCaptureOfLoggingFrom(logger) {
        logs => {
          eventually {
            logs.exists(_.getMessage.contains(PagerDutyKeys.UNKNOWN_PROCESSING_EXCEPTION)) shouldBe true
            logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION)) shouldBe true
          }
        }
      }
    }
  }

  "tryLock" should {
    "return a Future successful when lockRepository is able to lock and unlock successfully" in new Setup {
      val expectingResult: Future[Right[Nothing, String]] = Future.successful(Right("hello"))
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.successful(()))
      await(service.tryLock(expectingResult)) shouldBe Right("hello")
      verify(mockLockRepository, times(1)).takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(Matchers.eq(mongoLockId), Matchers.any())
    }

    s"return a $Right ${Seq.empty} is lock returns Future.successful (false)" in new Setup {
      val expectingResult: Future[Right[Nothing, String]] = Future.successful(Right("hello"))
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.successful(false))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Right(s"$jobName - JobAlreadyRunning")
        capturedLogEvents.exists(_.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
      }
      verify(mockLockRepository, times(1)).takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration))
      verify(mockLockRepository, times(0)).releaseLock(Matchers.eq(mongoLockId), Matchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and succeeds" in new Setup {
      val expectingResult: Future[Right[Nothing, String]] = Future.successful(Right("hello"))
      val exception = new Exception("woopsy")
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.successful(()))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        eventually {
          capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION))
        }
      }
      verify(mockLockRepository, times(1)).takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(Matchers.eq(mongoLockId), Matchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and failed also" in new Setup {
      val expectingResult: Future[Right[Nothing, String]] = Future.successful(Right("hello"))
      val exception = new Exception("not again")
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.failed(exception))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        eventually {
          capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION))
        }
      }
      verify(mockLockRepository, times(1)).takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(Matchers.eq(mongoLockId), Matchers.any())
    }
  }
}
