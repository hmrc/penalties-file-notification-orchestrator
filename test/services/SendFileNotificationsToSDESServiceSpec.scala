/*
 * Copyright 2022 HM Revenue & Customs
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
import models.FailedJobResponses.FailedToProcessNotifications
import models.SDESNotificationRecord
import models.notification.{RecordStatusEnum, SDESAudit, SDESChecksum, SDESNotification, SDESNotificationFile, SDESProperties}
import org.joda.time.Duration
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.Configuration
import play.api.test.Helpers._
import repositories.{FileNotificationRepository, LockRepositoryProvider, MongoLockResponses}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.lock.LockRepository
import utils.{LogCapturing, TimeMachine}
import utils.Logger.logger

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendFileNotificationsToSDESServiceSpec extends SpecBase with LogCapturing {
  val mockLockRepositoryProvider: LockRepositoryProvider = mock(classOf[LockRepositoryProvider])
  val mockLockRepository: LockRepository = mock(classOf[LockRepository])
  val mockSDESConnector: SDESConnector = mock(classOf[SDESConnector])
  val mockConfig: Configuration = mock(classOf[Configuration])
  val mockTimeMachine: TimeMachine = mock(classOf[TimeMachine])
  val mockFileNotificationRepository: FileNotificationRepository = mock(classOf[FileNotificationRepository])
  val jobName = "SendFileNotificationsToSDESJob"

  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = Duration.standardSeconds(mongoLockTimeout)

  val mockDateTime: LocalDateTime = LocalDateTime.of(2022, 1, 1, 0, 0, 0)
  val notification1: SDESNotification = SDESNotification(informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "ame", location = "someUrl", checksum = SDESChecksum(algorithm = "sha", value = "256"), size = 256, properties = Seq.empty[SDESProperties]
    ), audit = SDESAudit("file 1"))


  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.SENT,
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
    reset(mockLockRepository, mockLockRepositoryProvider, mockConfig, mockSDESConnector, mockFileNotificationRepository, mockTimeMachine, mockSDESConnector)
    val service = new SendFileNotificationsToSDESService(mockLockRepositoryProvider, mockFileNotificationRepository, mockSDESConnector, mockTimeMachine, mockConfig)

    when(mockConfig.get[Int](ArgumentMatchers.eq(s"schedules.${service.jobName}.mongoLockTimeout"))(ArgumentMatchers.any()))
      .thenReturn(mongoLockTimeout)
    when(mockTimeMachine.now).thenReturn(mockDateTime)

    if (withMongoLockStubs) {
      when(mockLockRepositoryProvider.repo).thenReturn(mockLockRepository)
      when(mockLockRepository.lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
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

    "process the notifications and return Right if they all succeed - only process notifications where nextAttemptAt <= now" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = LocalDateTime.now())
      ))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, "")))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.right.get shouldBe "Processed all notifications"
      verify(mockSDESConnector, times(2)).sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any())
      verify(mockFileNotificationRepository, times(2)).updateFileNotification(ArgumentMatchers.any())
    }

    "process the notifications and return Left if some fail" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = LocalDateTime.now())
      ))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, "")))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.get shouldBe FailedToProcessNotifications
      verify(mockSDESConnector, times(2)).sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any())
      verify(mockFileNotificationRepository, times(1)).updateFileNotification(ArgumentMatchers.any())
    }

    "process the notifications and return Left if all fail" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.get shouldBe FailedToProcessNotifications
    }
  }

  "tryLock" should {
    "return a Future successful when lockRepository is able to lock and unlock successfully" in new Setup {
      val expectingResult = Future.successful(Right(""))
      when(mockLockRepositoryProvider.repo).thenReturn(mockLockRepository)
      when(mockLockRepository.lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))
      await(service.tryLock(expectingResult)) shouldBe Right("")
      verify(mockLockRepository, times(1)).lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return a $Right ${Seq.empty} if lock returns Future.successful (false)" in new Setup {
      val expectingResult = Future.successful(Right(""))
      when(mockLockRepositoryProvider.repo).thenReturn(mockLockRepository)
      when(mockLockRepository.lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(false))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Right(s"$jobName - JobAlreadyRunning")

        capturedLogEvents.exists(_.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
      }

      verify(mockLockRepository, times(1)).lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(0)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and succeeds" in new Setup {
      val expectingResult = Future.successful(Right(""))
      val exception = new Exception("woopsy")
      when(mockLockRepositoryProvider.repo).thenReturn(mockLockRepository)
      when(mockLockRepository.lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
      }

      verify(mockLockRepository, times(1)).lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and failed also" in new Setup {
      val expectingResult = Future.successful(Right(""))
      val exception = new Exception("not again")
      when(mockLockRepositoryProvider.repo).thenReturn(mockLockRepository)
      when(mockLockRepository.lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.failed(exception))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
      }
      verify(mockLockRepository, times(1)).lock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }
  }
}
