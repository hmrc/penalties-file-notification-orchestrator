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
import config.AppConfig
import connectors.SDESConnector
import models.FailedJobResponses.{FailedToProcessNotifications, UnknownProcessingException}
import models.notification._
import models.{MongoLockResponses, SDESNotificationRecord}
import org.mockito.ArgumentMatchers
import org.scalatest.concurrent.Eventually.eventually
import play.api.Configuration
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys
import utils.{LogCapturing, TimeMachine}

import java.time.temporal.ChronoUnit.{HOURS, MINUTES}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class SendFileNotificationsToSDESServiceSpec extends SpecBase with LogCapturing {
  val mockLockRepository: MongoLockRepository = mock[MongoLockRepository]
  val mockSDESConnector: SDESConnector = mock[SDESConnector]
  val mockConfig: Configuration = mock[Configuration]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockTimeMachine: TimeMachine = mock[TimeMachine]
  val mockFileNotificationRepository: FileNotificationRepository = mock[FileNotificationRepository]
  val jobName = "SendFileNotificationsToSDESJob"

  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds

  val mockDateTime: Instant = LocalDateTime.of(2022, 1, 1, 0, 0, 0).toInstant(ZoneOffset.UTC)
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
    reference = "ref",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = mockDateTime.minus(1, HOURS),
    updatedAt = mockDateTime,
    nextAttemptAt = mockDateTime,
    notification = notification
  )

  val pendingNotifications: Seq[SDESNotificationRecord] = Seq(
    notificationRecord,
    notificationRecord.copy(nextAttemptAt = mockDateTime.minusSeconds(1)),
    notificationRecord.copy(nextAttemptAt = mockDateTime.plusSeconds(1))
  )

  class Setup(withMongoLockStubs: Boolean = true) {
    reset(mockLockRepository, mockConfig, mockSDESConnector, mockFileNotificationRepository, mockTimeMachine, mockSDESConnector, mockAppConfig)
    val service = new SendFileNotificationsToSDESService(mockLockRepository, mockFileNotificationRepository, mockSDESConnector, mockTimeMachine, mockConfig, mockAppConfig)
    when(mockConfig.get[Int](ArgumentMatchers.eq("notifications.retryThreshold"))(ArgumentMatchers.any())).thenReturn(5)
    when(mockConfig.get[Int](ArgumentMatchers.eq(s"schedules.${service.jobName}.mongoLockTimeout"))(ArgumentMatchers.any()))
      .thenReturn(mongoLockTimeout)
    when(mockTimeMachine.dateTimeNow).thenReturn(LocalDateTime.of(2022, 1, 1, 0, 0, 0))
    when(mockTimeMachine.now).thenReturn(mockDateTime)
    when(mockAppConfig.numberOfNotificationsToSendInBatch).thenReturn(10)
    if (withMongoLockStubs) {
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
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
      result.toOption.get shouldBe "Processed all notifications"
    }

    "process the notifications and return Right if they all succeed - only process PENDING notifications where nextAttemptAt <= now" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = LocalDateTime.now().toInstant(ZoneOffset.UTC))
      ))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.toOption.get shouldBe "Processed all notifications"
      verify(mockSDESConnector, times(2)).sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any())
      verify(mockFileNotificationRepository, times(2)).updateFileNotification(ArgumentMatchers.any())
    }

    "process the notification and return Right if they all succeed - limit notifications to 10 " +
      "(if more are able to be sent - to prevent overloading downstream)" in new Setup {
      val notifications: Seq[SDESNotificationRecord] = (1 to 15).map(idx => notificationRecord.copy(reference = s"ref$idx"))
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notifications))
      when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = LocalDateTime.now().toInstant(ZoneOffset.UTC))
      ))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isRight shouldBe true
          result.toOption.get shouldBe "Processed all notifications"
          verify(mockSDESConnector, times(10)).sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any())
          verify(mockFileNotificationRepository, times(10)).updateFileNotification(ArgumentMatchers.any())
          logs.exists(_.getMessage == "[SendFileNotificationsToSDESService][logDifferenceInLimitedNotificationsVersusFilteredNotifications] - " +
            "Number of notifications exceeded limit (of 10). Number of notifications that were ready to send was: 15. Only 10 will be sent.")
        }
      }
    }

    "process the notifications and return Left if some fail" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = LocalDateTime.now().toInstant(ZoneOffset.UTC))
      ))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")), Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe FailedToProcessNotifications
          eventually {
            logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_5XX_FROM_SDES.toString)) shouldBe true
            logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION.toString)) shouldBe true
          }
        }
      }
      verify(mockSDESConnector, times(2)).sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any())
      val updatedNotificationRecordSent: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.SENT, updatedAt = mockDateTime)
      val updatedNotificationRecordPending: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PENDING, updatedAt = mockDateTime, numberOfAttempts = 2, nextAttemptAt = mockDateTime.plus(30, MINUTES).minusSeconds(1))
      verify(mockFileNotificationRepository, times(1)).updateFileNotification(ArgumentMatchers.eq(updatedNotificationRecordSent))
      verify(mockFileNotificationRepository, times(1)).updateFileNotification(ArgumentMatchers.eq(updatedNotificationRecordPending))
    }

    "process the notifications and return Left if all fail" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.any()))
        .thenReturn(Future.successful(notificationRecord))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe FailedToProcessNotifications
          eventually {
            logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_5XX_FROM_SDES.toString)) shouldBe true
            logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION.toString)) shouldBe true
          }
        }
      }
      verify(mockFileNotificationRepository, times(2)).updateFileNotification(ArgumentMatchers.any())

    }

    "set the record to be a PERMANENT_FAILURE" when {
      "the threshold has been met" in new Setup {
        val notificationsToSend: Seq[SDESNotificationRecord] = Seq(
          notificationRecord.copy(numberOfAttempts = 5)
        )
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 5,
          status = RecordStatusEnum.PERMANENT_FAILURE, updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
        when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result = await(service.invoke)
            result.isRight shouldBe true
            result.toOption.get shouldBe "Processed all notifications"
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.NOTIFICATION_SET_TO_PERMANENT_FAILURE.toString)) shouldBe true
            }
          }
        }
      }

      "a 4xx response has been received" in new Setup {
        val notificationsToSend: Seq[SDESNotificationRecord] = Seq(notificationRecord)
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PERMANENT_FAILURE,
          updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
        when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))
        when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result = await(service.invoke)
            result.isLeft shouldBe true
            result.left.toOption.get shouldBe FailedToProcessNotifications
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_4XX_FROM_SDES.toString)) shouldBe true
            }
          }
        }
      }

      "a 5xx response has been received" in new Setup {
        val notificationsToSend: Seq[SDESNotificationRecord] = Seq(notificationRecord)
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PERMANENT_FAILURE,
          updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
        when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
        when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result = await(service.invoke)
            result.isLeft shouldBe true
            result.left.toOption.get shouldBe FailedToProcessNotifications
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.RECEIVED_5XX_FROM_SDES.toString)) shouldBe true
            }
          }
        }
      }

      "an unknown exception has occurred" in new Setup {
        val notificationsToSend: Seq[SDESNotificationRecord] = Seq(notificationRecord)
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PERMANENT_FAILURE,
          updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
        when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.failed(new Exception("i broke")))
        when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result = await(service.invoke)
            result.isLeft shouldBe true
            result.left.toOption.get shouldBe FailedToProcessNotifications
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.UNKNOWN_EXCEPTION_FROM_SDES.toString)) shouldBe true
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
        nextAttemptAt = mockDateTime.plus(30, MINUTES))
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(notificationsToSend))
      when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
      when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.eq(notificationRecordIncreasedAttempt)))
        .thenReturn(Future.successful(notificationRecordIncreasedAttempt))
      val result = await(service.invoke)
      result.isLeft shouldBe true
      result.left.toOption.get shouldBe FailedToProcessNotifications
    }

    s"return $Left $UnknownProcessingException" when {
      "the repository fails to retrieve notifications" in new Setup {
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.failed(new Exception("I broke")))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result = await(service.invoke)
            result.isLeft shouldBe true
            result.left.toOption.get shouldBe UnknownProcessingException
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.UNKNOWN_PROCESSING_EXCEPTION.toString)) shouldBe true
            }
          }
        }
      }

      "an unknown status is returned from SDES" in new Setup {
        val notificationRecordAsPermanentFailure: SDESNotificationRecord = notificationRecord.copy(status = RecordStatusEnum.PERMANENT_FAILURE,
          updatedAt = mockDateTime)
        when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(Seq(notificationRecord)))
        when(mockSDESConnector.sendNotificationToSDES(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(MOVED_PERMANENTLY, "")))
        when(mockFileNotificationRepository.updateFileNotification(ArgumentMatchers.eq(notificationRecordAsPermanentFailure)))
          .thenReturn(Future.successful(notificationRecordAsPermanentFailure))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result = await(service.invoke)
            result.isLeft shouldBe true
            result.left.toOption.get shouldBe FailedToProcessNotifications
            eventually {
              logs.exists(_.getMessage.contains(s"Unknown status ($MOVED_PERMANENTLY) returned when sending file (with reference: ${notificationRecord.reference}) to SDES")) shouldBe true
              logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION.toString)) shouldBe true
            }
          }
        }
      }
    }
  }

  "tryLock" should {
    s"return $Right when lockRepository is able to lock and unlock successfully" in new Setup {
      val expectingResult = Future.successful(Right(""))
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))
      await(service.tryLock(expectingResult)) shouldBe Right("")
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return a $Right ${Seq.empty} if lock returns false" in new Setup {
      val expectingResult = Future.successful(Right(""))
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(false))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Right(s"$jobName - JobAlreadyRunning")
        capturedLogEvents.exists(_.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
      }
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(0)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and succeeds" in new Setup {
      val expectingResult = Future.successful(Right(""))
      val exception = new Exception("woopsy")
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION.toString)) shouldBe true
      }
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and failed also" in new Setup {
      val expectingResult = Future.successful(Right(""))
      val exception = new Exception("not again")
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.failed(exception))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION.toString)) shouldBe true
      }
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
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
      service.updateNextAttemptAtTimestamp(notificationRecordWithZeroAttempts) shouldBe mockDateTime.plus(1, MINUTES)
    }

    "add 30 minutes if the numberOfAttempts is 1" in new Setup {
      val notificationRecordWithOneAttempt: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 1)
      service.updateNextAttemptAtTimestamp(notificationRecordWithOneAttempt) shouldBe mockDateTime.plus(30, MINUTES)
    }

    "add 2 hours if the numberOfAttempts is 2" in new Setup {
      val notificationRecordWithTwoAttempts: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 2)
      service.updateNextAttemptAtTimestamp(notificationRecordWithTwoAttempts) shouldBe mockDateTime.plus(2, HOURS)
    }

    "add 4 hours if the numberOfAttempts is 3" in new Setup {
      val notificationRecordWithThreeAttempts: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 3)
      service.updateNextAttemptAtTimestamp(notificationRecordWithThreeAttempts) shouldBe mockDateTime.plus(4, HOURS)
    }

    "add 8 hours if the numberOfAttempts is 4" in new Setup {
      val notificationRecordWithFourAttempts: SDESNotificationRecord = notificationRecord.copy(numberOfAttempts = 4)
      service.updateNextAttemptAtTimestamp(notificationRecordWithFourAttempts) shouldBe mockDateTime.plus(8, HOURS)
    }
  }
}
