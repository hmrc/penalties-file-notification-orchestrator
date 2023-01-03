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

import base.SpecBase
import connectors.SDESConnector
import models.FailedJobResponses.{FailedToProcessNotifications, UnknownProcessingException}
import models.notification._
import models.{MongoLockResponses, SDESNotificationRecord}
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.Configuration
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.Logger.logger
import utils.{LogCapturing, TimeMachine}
import java.time.LocalDateTime

import org.scalatest.concurrent.Eventually.eventually
import utils.PagerDutyHelper.PagerDutyKeys

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class SendFileNotificationsToSDESServiceSpec extends SpecBase with LogCapturing {
  val mockLockRepository: MongoLockRepository = mock(classOf[MongoLockRepository])
  val mockSDESConnector: SDESConnector = mock(classOf[SDESConnector])
  val mockConfig: Configuration = mock(classOf[Configuration])
  val mockTimeMachine: TimeMachine = mock(classOf[TimeMachine])
  val mockFileNotificationRepository: FileNotificationRepository = mock(classOf[FileNotificationRepository])
  val jobName = "SendFileNotificationsToSDESJob"

  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds

  val mockDateTime: LocalDateTime = LocalDateTime.of(2022, 1, 1, 0, 0, 0)
  val notification1: SDESNotification = SDESNotification(informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "ame", location = "someUrl", checksum = SDESChecksum(algorithm = "sha", value = "256"), size = 256, properties = Seq.empty[SDESProperties]
    ), audit = SDESAudit("file 1"))


  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = mockDateTime.minusHours(1),
    updatedAt = mockDateTime,
    nextAttemptAt = mockDateTime,
    notification = notification1
  )

  val pendingNotifications: Seq[SDESNotificationRecord] = Seq(
    notificationRecord,
    notificationRecord.copy(nextAttemptAt = mockDateTime.minusSeconds(1)),
    notificationRecord.copy(nextAttemptAt = mockDateTime.plusSeconds(1))
  )

  class Setup(withMongoLockStubs: Boolean = true) {
    reset(mockLockRepository, mockConfig, mockSDESConnector, mockFileNotificationRepository, mockTimeMachine, mockSDESConnector)
    val service = new SendFileNotificationsToSDESService(mockLockRepository, mockFileNotificationRepository, mockSDESConnector, mockTimeMachine, mockConfig)

    when(mockConfig.get[Int](Matchers.eq(s"schedules.${service.jobName}.mongoLockTimeout"))(Matchers.any()))
      .thenReturn(mongoLockTimeout)
    when(mockTimeMachine.now).thenReturn(mockDateTime)

    if (withMongoLockStubs) {
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.successful(()))
    }
  }

  "invoke" should {
    "run the job successfully if there is no notifications" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(Seq.empty))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.right.get shouldBe "Processed all notifications"
    }

    "process the notifications and return Right if they all succeed - only process PENDING notifications where nextAttemptAt <= now" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = LocalDateTime.now())
      ))
      when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.right.get shouldBe "Processed all notifications"
      verify(mockSDESConnector, times(2)).sendNotificationToSDES(Matchers.any())(Matchers.any())
      verify(mockFileNotificationRepository, times(2)).updateFileNotification(Matchers.any())
    }

    "process the notifications and return Left if some fail" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = LocalDateTime.now())
      ))
      when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.get shouldBe FailedToProcessNotifications
      withCaptureOfLoggingFrom(logger) {
        logs => {
          eventually {
            logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_5XX_FROM_SDES))
            logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION))
          }
        }
      }
      verify(mockSDESConnector, times(2)).sendNotificationToSDES(Matchers.any())(Matchers.any())
      val updatedNotificationRecordSent: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = mockDateTime)
      val updatedNotificationRecordPending: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PENDING, updatedAt = mockDateTime, numberOfAttempts = 2, nextAttemptAt = mockDateTime.plusMinutes(30).minusSeconds(1))
      verify(mockFileNotificationRepository, times(1)).updateFileNotification(Matchers.eq(updatedNotificationRecordSent))
      verify(mockFileNotificationRepository, times(1)).updateFileNotification(Matchers.eq(updatedNotificationRecordPending))
    }

    "process the notifications and return Left if all fail" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.any()))
        .thenReturn(Future.successful(notificationRecord))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.get shouldBe FailedToProcessNotifications
      withCaptureOfLoggingFrom(logger) {
        logs => {
          eventually {
            logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_5XX_FROM_SDES))
            logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION))
          }
        }
      }
      verify(mockFileNotificationRepository, times(2)).updateFileNotification(Matchers.any())

    }

    "set the record to be a PERMANENT_FAILURE" when {
      "the threshold has been met and there is a failure" in new Setup {
        val notificationsToSend: Seq[SDESNotificationRecord] = Seq(
          notificationRecord.copy(numberOfAttempts = 5)
        )
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 5,
          status = RecordStatusEnum.PERMANENT_FAILURE, updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
        when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
        when(mockFileNotificationRepository.updateFileNotification(Matchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        val result = await(service.invoke)
        result.isLeft shouldBe true
        result.left.get shouldBe FailedToProcessNotifications
        withCaptureOfLoggingFrom(logger) {
          logs => {
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_5XX_FROM_SDES))
              logs.exists(_.getMessage.contains(PagerDutyKeys.NOTIFICATION_SET_TO_PERMANENT_FAILURE))
            }
          }
        }
      }

      "a 4xx response has been received" in new Setup {
        val notificationsToSend: Seq[SDESNotificationRecord] = Seq(
          notificationRecord
        )
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PERMANENT_FAILURE,
          updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
        when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))
        when(mockFileNotificationRepository.updateFileNotification(Matchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        val result = await(service.invoke)
        result.isLeft shouldBe true
        result.left.get shouldBe FailedToProcessNotifications
        withCaptureOfLoggingFrom(logger) {
          logs => {
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_4XX_FROM_SDES))
            }
          }
        }
      }

      "an unknown exception has occurred" in new Setup {
        val notificationsToSend: Seq[SDESNotificationRecord] = Seq(
          notificationRecord
        )
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PERMANENT_FAILURE,
          updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
        when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
          .thenReturn(Future.failed(new Exception("i broke")))
        when(mockFileNotificationRepository.updateFileNotification(Matchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        val result = await(service.invoke)
        result.isLeft shouldBe true
        result.left.get shouldBe FailedToProcessNotifications
        withCaptureOfLoggingFrom(logger) {
          logs => {
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.UNKNOWN_EXCEPTION_FROM_SDES))
            }
          }
        }
      }
    }

    "increment the retries of a record and set the nextAttemptAt timestamp when the record is below the retry threshold" in new Setup {
      val notificationsToSend: Seq[SDESNotificationRecord] = Seq(
        notificationRecord
      )
      val notificationRecordIncreasedAttempt: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 2, status = RecordStatusEnum.PENDING,
        nextAttemptAt = mockDateTime.plusMinutes(30))
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
      when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.eq(notificationRecordIncreasedAttempt)))
        .thenReturn(Future.successful(notificationRecordIncreasedAttempt))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.get shouldBe FailedToProcessNotifications
    }

    s"retrun $Left $UnknownProcessingException when the repository fails to retrieve notifications" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.failed(new Exception("I broke")))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.get shouldBe UnknownProcessingException
      withCaptureOfLoggingFrom(logger) {
        logs => {
          eventually {
            logs.exists(_.getMessage.contains(PagerDutyKeys.UNKNOWN_PROCESSING_EXCEPTION))
          }
        }
      }
    }
  }

  "tryLock" should {
    "return a Future successful when lockRepository is able to lock and unlock successfully" in new Setup {
      val expectingResult = Future.successful(Right(""))
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.successful(()))
      await(service.tryLock(expectingResult)) shouldBe Right("")
      verify(mockLockRepository, times(1)).takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(Matchers.eq(mongoLockId), Matchers.any())
    }

    s"return a $Right ${Seq.empty} if lock returns Future.successful (false)" in new Setup {
      val expectingResult = Future.successful(Right(""))
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
      val expectingResult = Future.successful(Right(""))
      val exception = new Exception("woopsy")
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.successful(()))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION))
      }

      verify(mockLockRepository, times(1)).takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(Matchers.eq(mongoLockId), Matchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and failed also" in new Setup {
      val expectingResult = Future.successful(Right(""))
      val exception = new Exception("not again")
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.failed(exception))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION))
      }
      verify(mockLockRepository, times(1)).takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(Matchers.eq(mongoLockId), Matchers.any())
    }
  }

  "setRecordToPermanentFailure" should {
    s"set the status of the record to ${RecordStatusEnum.PERMANENT_FAILURE} and set the updatedAt time" in new Setup {
      val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(
        status = RecordStatusEnum.PERMANENT_FAILURE,
        updatedAt = mockDateTime
      )
      service.setRecordToPermanentFailure(notificationRecord) shouldBe notificationRecordAsPermanentFailure
    }
  }

  "updateNextAttemptAtTimestamp" should {
    "add 1 minute if the numberOfAttempts is 0" in new Setup {
      val notificationRecordWithZeroAttempts: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 0)
      service.updateNextAttemptAtTimestamp(notificationRecordWithZeroAttempts) shouldBe mockDateTime.plusMinutes(1)
    }

    "add 30 minutes if the numberOfAttempts is 1" in new Setup {
      val notificationRecordWithOneAttempt: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 1)
      service.updateNextAttemptAtTimestamp(notificationRecordWithOneAttempt) shouldBe mockDateTime.plusMinutes(30)
    }

    "add 2 hours if the numberOfAttempts is 2" in new Setup {
      val notificationRecordWithTwoAttempts: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 2)
      service.updateNextAttemptAtTimestamp(notificationRecordWithTwoAttempts) shouldBe mockDateTime.plusHours(2)
    }

    "add 4 hours if the numberOfAttempts is 3" in new Setup {
      val notificationRecordWithThreeAttempts: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 3)
      service.updateNextAttemptAtTimestamp(notificationRecordWithThreeAttempts) shouldBe mockDateTime.plusHours(4)
    }

    "add 8 hours if the numberOfAttempts is 4" in new Setup {
      val notificationRecordWithFourAttempts: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 4)
      service.updateNextAttemptAtTimestamp(notificationRecordWithFourAttempts) shouldBe mockDateTime.plusHours(8)
    }
  }
}
