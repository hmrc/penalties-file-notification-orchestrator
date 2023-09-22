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

import helpers.SDESStub
import models.FailedJobResponses.FailedToProcessNotifications
import models.SDESNotificationRecord
import models.notification._
import org.mongodb.scala.Document
import org.scalatest.matchers.should.Matchers._
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys.NOTIFICATION_SET_TO_PERMANENT_FAILURE
import utils.{IntegrationSpecCommonBase, LogCapturing}

import java.time.temporal.ChronoUnit.{MINUTES, SECONDS}
import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
import scala.concurrent.duration.DurationInt

class SendFileNotificationsToSDESServiceISpec extends IntegrationSpecCommonBase with LogCapturing {
  val lockRepository: MongoLockRepository = injector.instanceOf[MongoLockRepository]
  val service: SendFileNotificationsToSDESService = injector.instanceOf[SendFileNotificationsToSDESService]
  val notificationRepo: FileNotificationRepository = injector.instanceOf[FileNotificationRepository]

  class Setup {
    await(notificationRepo.collection.deleteMany(Document()).toFuture())
    await(lockRepository.collection.deleteMany(Document()).toFuture())
    await(lockRepository.ensureIndexes())
    await(lockRepository.collection.countDocuments().toFuture()) shouldBe 0
  }

  lazy val dateTimeOfNow: Instant = LocalDateTime.now(ZoneId.of("UTC")).truncatedTo(SECONDS).toInstant(ZoneOffset.UTC)

  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020,1,1,1,1).toInstant(ZoneOffset.UTC),
    updatedAt = LocalDateTime.of(2020,2,2,2,2).toInstant(ZoneOffset.UTC),
    nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC),
    notification = sampleNotification
  )

  val pendingNotifications: Seq[SDESNotificationRecord] = Seq(
    notificationRecord,
    notificationRecord.copy(reference = "ref1", updatedAt = dateTimeOfNow, nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC)),
    notificationRecord.copy(reference = "ref2", nextAttemptAt = dateTimeOfNow.plus(2, MINUTES)),
    notificationRecord.copy(reference = "ref3", nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC), status = RecordStatusEnum.FAILED_PENDING_RETRY),
    notificationRecord.copy(reference = "ref4", nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC), status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY),
    notificationRecord.copy(reference = "ref5", nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC), status = RecordStatusEnum.FILE_NOT_RECEIVED_IN_SDES_PENDING_RETRY)
  )

  "tryLock" should {
    "not do anything if the job is already locked" in new Setup {
      val randomServerId = "123"
      val releaseDuration = 123.seconds
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 0
      await(lockRepository.takeLock(service.lockKeeper.lockId, randomServerId, releaseDuration))
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 1

      await(service.invoke).toOption.get shouldBe s"${service.jobName} - JobAlreadyRunning"
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 1
    }
  }

  "invoke" should {
    "run the job successfully if there is no notifications" in new Setup {
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.toOption.get shouldBe "Processed all notifications"
    }

    "process the notifications and return Right if they all succeed - only process notifications where nextAttemptAt <= now" in new Setup {
      SDESStub.successfulStubResponse()
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.toOption.get shouldBe "Processed all notifications"
      val notificationsInRepo: Seq[SDESNotificationRecord] = await(notificationRepo.collection.find().map(SDESNotificationRecord.decrypt(_)).toFuture())
      notificationsInRepo.exists(_.equals(notificationRecord.copy(reference = "ref2", nextAttemptAt = dateTimeOfNow.plus(2, MINUTES)))) shouldBe true
      notificationsInRepo.find(_.reference == "ref1").get.updatedAt.isAfter(dateTimeOfNow) shouldBe true
      notificationsInRepo.find(_.reference == "ref").get.updatedAt.isAfter(dateTimeOfNow) shouldBe true
      notificationsInRepo.find(_.reference == "ref3").get.updatedAt.isAfter(dateTimeOfNow) shouldBe true
      notificationsInRepo.find(_.reference == "ref4").get.updatedAt.isAfter(dateTimeOfNow) shouldBe true
      notificationsInRepo.find(_.reference == "ref5").get.updatedAt.isAfter(dateTimeOfNow) shouldBe true
      notificationsInRepo.count(_.status == RecordStatusEnum.SENT) shouldBe 5
    }

    "process the notifications and set records to permanent failure when the threshold is met or exceeded" in new Setup {
      val notifications: Seq[SDESNotificationRecord] = Seq(
        notificationRecord.copy(numberOfAttempts = 5),
        notificationRecord.copy(reference = "ref1", updatedAt = dateTimeOfNow, numberOfAttempts = 5),
        notificationRecord.copy(reference = "ref3", updatedAt = dateTimeOfNow, numberOfAttempts = 5, status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY),
        notificationRecord.copy(reference = "ref4", updatedAt = dateTimeOfNow, numberOfAttempts = 5, status = RecordStatusEnum.FAILED_PENDING_RETRY),
        notificationRecord.copy(reference = "ref5", updatedAt = dateTimeOfNow, numberOfAttempts = 4, status = RecordStatusEnum.FAILED_PENDING_RETRY),
        notificationRecord.copy(reference = "ref2", nextAttemptAt = dateTimeOfNow.plus(2, MINUTES))
      )
      SDESStub.successfulStubResponse()
      await(notificationRepo.insertFileNotifications(notifications))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isRight shouldBe true
          result.toOption.get shouldBe "Processed all notifications"
          logs.exists(_.getMessage.contains(NOTIFICATION_SET_TO_PERMANENT_FAILURE.toString)) shouldBe true
          val notificationsInRepo: Seq[SDESNotificationRecord] = await(notificationRepo.collection.find(Document()).toFuture())
          val firstNotification: SDESNotificationRecord = notificationsInRepo.find(_.reference == "ref").get
          val secondNotification: SDESNotificationRecord = notificationsInRepo.find(_.reference == "ref1").get
          val thirdNotification: SDESNotificationRecord = notificationsInRepo.find(_.reference == "ref3").get
          val fourthNotification: SDESNotificationRecord = notificationsInRepo.find(_.reference == "ref4").get
          val fifthNotification: SDESNotificationRecord = notificationsInRepo.find(_.reference == "ref2").get
          val sixthNotification: SDESNotificationRecord = notificationsInRepo.find(_.reference == "ref5").get
          firstNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
          secondNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
          thirdNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
          fourthNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
          fifthNotification.status shouldBe RecordStatusEnum.PENDING
          sixthNotification.status shouldBe RecordStatusEnum.SENT
        }
      }
    }

    "process the notifications and return Left if there are failures due to 5xx response - increasing retries " +
      "if below threshold (number of attempts = 1)" in new Setup {
      SDESStub.failedStubResponse(INTERNAL_SERVER_ERROR)
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe FailedToProcessNotifications
          logs.exists(_.getMessage.equals("[SendFileNotificationsToSDESService][invoke] - Received 500 status code from connector call to SDES with response body: Something broke")) shouldBe true
          val pendingNotificationsInRepo: Seq[SDESNotificationRecord] = await(notificationRepo.getPendingNotifications())
          val firstNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref").get
          val secondNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref1").get
          val thirdNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref3").get
          val fourthNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref4").get
          firstNotification.nextAttemptAt shouldBe LocalDateTime.of(2020,3,3,3,33).toInstant(ZoneOffset.UTC)
          secondNotification.nextAttemptAt shouldBe LocalDateTime.of(2020,3,3,3,33).toInstant(ZoneOffset.UTC)
          thirdNotification.nextAttemptAt shouldBe LocalDateTime.of(2020,3,3,3,33).toInstant(ZoneOffset.UTC)
          fourthNotification.nextAttemptAt shouldBe LocalDateTime.of(2020,3,3,3,33).toInstant(ZoneOffset.UTC)
          firstNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          secondNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          thirdNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          fourthNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          firstNotification.numberOfAttempts shouldBe 2
          secondNotification.numberOfAttempts shouldBe 2
          thirdNotification.numberOfAttempts shouldBe 2
          fourthNotification.numberOfAttempts shouldBe 2
        }
      }
    }

    "process the notifications and return Left if there are failures due to 4xx response - increasing retries " +
      "if below threshold (number of attempts = 1)" in new Setup {
      SDESStub.failedStubResponse(BAD_REQUEST)
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe FailedToProcessNotifications
          logs.exists(_.getMessage.contains(s"[SendFileNotificationsToSDESService][invoke] - Received 400 status code from connector call to SDES with response body: Something broke")) shouldBe true
          val pendingNotificationsInRepo: Seq[SDESNotificationRecord] = await(notificationRepo.collection.find(Document()).toFuture())
          val firstNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref").get
          val secondNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref1").get
          val thirdNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref3").get
          val fourthNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref4").get
          firstNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          secondNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          thirdNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          fourthNotification.updatedAt.isAfter(dateTimeOfNow) shouldBe true
          firstNotification.numberOfAttempts shouldBe 2
          secondNotification.numberOfAttempts shouldBe 2
          thirdNotification.numberOfAttempts shouldBe 2
          fourthNotification.numberOfAttempts shouldBe 2
        }
      }
    }

    "process the notifications and return Left if there are failures due to unexpected response and set the records to permanent failure" in new Setup {
      SDESStub.failedStubResponse(SEE_OTHER)
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.toOption.get shouldBe FailedToProcessNotifications
          logs.exists(_.getMessage.contains(s"[SendFileNotificationsToSDESService][invoke] - Exception occurred processing notifications")) shouldBe true
          val pendingNotificationsInRepo: Seq[SDESNotificationRecord] = await(notificationRepo.collection.find(Document()).toFuture())
          val firstNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref").get
          val secondNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref1").get
          val thirdNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref3").get
          val fourthNotification: SDESNotificationRecord = pendingNotificationsInRepo.find(_.reference == "ref4").get
          firstNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
          secondNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
          thirdNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
          fourthNotification.status shouldBe RecordStatusEnum.PERMANENT_FAILURE
        }
      }
    }
  }
}
