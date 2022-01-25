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

import helpers.SDESStub
import models.FailedJobResponses.FailedToProcessNotifications
import models.SDESNotificationRecord
import models.notification.{RecordStatusEnum, SDESAudit, SDESChecksum, SDESNotification, SDESNotificationFile, SDESProperties}
import org.joda.time.Duration
import org.mongodb.scala.Document
import play.api.test.Helpers._
import repositories.{FileNotificationRepository, LockRepositoryProvider}
import uk.gov.hmrc.lock.LockRepository
import utils.{IntegrationSpecCommonBase, LogCapturing}
import org.scalatest.matchers.should.Matchers._
import utils.Logger.logger

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class SendFileNotificationsToSDESServiceISpec extends IntegrationSpecCommonBase with LogCapturing {
  val lockRepositoryProviderRepo: LockRepository = injector.instanceOf[LockRepositoryProvider].repo
  val service: SendFileNotificationsToSDESService = injector.instanceOf[SendFileNotificationsToSDESService]
  val notificationRepo: FileNotificationRepository = injector.instanceOf[FileNotificationRepository]

  class Setup {
    await(notificationRepo.collection.deleteMany(Document()).toFuture())
    await(lockRepositoryProviderRepo.drop)
    await(lockRepositoryProviderRepo.ensureIndexes)
    await(lockRepositoryProviderRepo.count) shouldBe 0
  }

  val notification1: SDESNotification = SDESNotification(informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "ame", location = "someUrl", checksum = SDESChecksum(algorithm = "sha", value = "256"), size = 256, properties = Seq.empty[SDESProperties]
    ), audit = SDESAudit("file 1"))

  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020,1,1,1,1),
    updatedAt = LocalDateTime.of(2020,2,2,2,2),
    nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
    notification = notification1
  )

  val pendingNotifications = Seq(
    notificationRecord,
    notificationRecord.copy(reference = "ref1"),
    notificationRecord.copy(reference = "ref2", nextAttemptAt = LocalDateTime.now().plusMinutes(2))
  )

  "tryLock" should {
    "not do anything if the job is already locked" in new Setup {
      val randomServerId = "123"
      val releaseDuration = Duration.standardSeconds(123)
      await(lockRepositoryProviderRepo.count) shouldBe 0
      await(lockRepositoryProviderRepo.lock(service.lockKeeper.lockId, randomServerId, releaseDuration))
      await(lockRepositoryProviderRepo.count) shouldBe 1

      await(service.invoke).right.get shouldBe s"${service.jobName} - JobAlreadyRunning"
      await(lockRepositoryProviderRepo.count) shouldBe 1
    }
  }

  "invoke" should {
    "run the job successfully if there is no notifications" in new Setup {
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.right.get shouldBe "Processed all notifications"
    }

    "process the notifications and return Right if they all succeed - only process notifications where nextAttemptAt <= now" in new Setup {
      SDESStub.successfulStubResponse()
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.right.get shouldBe "Processed all notifications"
      //TODO: check repo to check status change for only eligible notifications
    }

    "process the notifications and return Left if there are failures due to 5xx response" in new Setup {
      SDESStub.failedStubResponse(INTERNAL_SERVER_ERROR)
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.get shouldBe FailedToProcessNotifications
          logs.exists(_.getMessage.equals("[SendFileNotificationsToSDESService][invoke] - Received 5xx status (500) from connector call to SDES")) shouldBe true
        }
      }
    }

    "process the notifications and return Left if there are failures due to 4xx response" in new Setup {
      SDESStub.failedStubResponse(BAD_REQUEST)
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.get shouldBe FailedToProcessNotifications
          logs.exists(_.getMessage.equals(s"[SendFileNotificationsToSDESService][invoke] - Received 4xx status (400) from connector call to SDES")) shouldBe true
        }
      }
    }

    "process the notifications and return Left if there are failures due to unexpected response" in new Setup {
      SDESStub.failedStubResponse(SEE_OTHER)
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      withCaptureOfLoggingFrom(logger) {
        logs => {
          val result = await(service.invoke)
          result.isLeft shouldBe true
          result.left.get shouldBe FailedToProcessNotifications
          logs.exists(_.getMessage.contains(s"[SendFileNotificationsToSDESService][invoke] - Exception occurred processing notifications")) shouldBe true
        }
      }
    }
  }
}
