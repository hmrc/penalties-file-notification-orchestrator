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
import java.time.temporal.ChronoUnit

import helpers.SDESStub
import models.SDESNotificationRecord
import models.notification.RecordStatusEnum
import org.mongodb.scala.Document
import org.scalatest.matchers.should.Matchers._
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.{IntegrationSpecCommonBase, LogCapturing}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class NotProcessedFilesServiceISpec extends IntegrationSpecCommonBase with LogCapturing {
  val lockRepository = injector.instanceOf[MongoLockRepository]
  val service = injector.instanceOf[NotProcessedFilesService]
  val notificationRepo = injector.instanceOf[FileNotificationRepository]

  class Setup {
    await(notificationRepo.collection.deleteMany(Document()).toFuture())
    await(lockRepository.collection.deleteMany(Document()).toFuture())
    await(lockRepository.ensureIndexes)
    await(lockRepository.collection.countDocuments().toFuture()) shouldBe 0
  }

  lazy val dateTimeOfNow: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020,1,1,1,1),
    updatedAt = LocalDateTime.of(2020,2,2,2,2),
    nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
    notification = sampleNotification
  )

  val pendingNotifications: Seq[SDESNotificationRecord] = Seq(
    notificationRecord,
    notificationRecord.copy(reference = "ref1", updatedAt = dateTimeOfNow, nextAttemptAt = LocalDateTime.of(2020,3,3,3,3)),
    notificationRecord.copy(reference = "ref2", nextAttemptAt = dateTimeOfNow.plusMinutes(2)),
    notificationRecord.copy(reference = "ref3", nextAttemptAt = LocalDateTime.of(2020,3,3,3,3), status = RecordStatusEnum.FAILED_PENDING_RETRY),
    notificationRecord.copy(reference = "ref4", nextAttemptAt = LocalDateTime.of(2020,3,3,3,3), status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY)
  )

  "tryLock" should {
    "not do anything if the job is already locked" in new Setup {
      val randomServerId = "123"
      val releaseDuration: FiniteDuration = 123.seconds
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 0
      await(lockRepository.takeLock(service.lockKeeper.lockId, randomServerId, releaseDuration))
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 1

      await(service.invoke).getOrElse("fail") shouldBe s"${service.jobName} - JobAlreadyRunning"
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 1
    }
  }

  "invoke" should {
    "run the job successfully if there is not notifications" in new Setup {
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.getOrElse("fail") shouldBe "Processed all notifications"
    }

    "process the notifications and return Right is they all succeed - only process notifications where nextAttempt < now" in new Setup {
      SDESStub.successfulStubResponse()
      await(notificationRepo.insertFileNotifications(pendingNotifications))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.getOrElse("fail") shouldBe "Processed all notifications"
      val notificationsInRepo = await(notificationRepo.collection.find(Document()).toFuture())
      notificationsInRepo.exists(_.equals(notificationRecord.copy(reference = "ref2", updatedAt = dateTimeOfNow.plusMinutes(2) ,status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY)))
      notificationsInRepo.find(_.reference == "ref1").get.status shouldBe RecordStatusEnum.PENDING
    }
  }
}
