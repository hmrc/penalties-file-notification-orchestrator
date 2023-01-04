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

import models.SDESNotificationRecord
import models.notification._
import org.mongodb.scala.Document
import org.scalatest.matchers.should.Matchers._
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.Logger.logger
import utils.{IntegrationSpecCommonBase, LogCapturing}

import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt

class MonitoringJobISpec extends IntegrationSpecCommonBase with LogCapturing {
  class Setup {
    val lockRepository: MongoLockRepository = injector.instanceOf[MongoLockRepository]
    val service: MonitoringJobService = app.injector.instanceOf[MonitoringJobService]
    val repo: FileNotificationRepository = app.injector.instanceOf[FileNotificationRepository]
    await(lockRepository.collection.deleteMany(Document()).toFuture())
    await(lockRepository.ensureIndexes)
    await(lockRepository.collection.countDocuments().toFuture()) shouldBe 0
    await(repo.collection.deleteMany(Document()).toFuture())
    await(repo.collection.countDocuments().toFuture()) shouldBe 0
  }

  "tryLock" should {
    "not do anything if the job is already locked" in new Setup {
      val randomServerId = "123"
      val releaseDuration = 123.seconds
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 0
      await(lockRepository.takeLock(service.lockKeeper.lockId, randomServerId, releaseDuration))
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 1

      await(service.invoke).right.get shouldBe Seq(s"${service.jobName} - JobAlreadyRunning")
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 1
    }
  }

  val notification1: SDESNotification = SDESNotification(informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "ame", location = "someUrl", checksum = SDESChecksum(algorithm = "sha", value = "256"), size = 256, properties = Seq.empty[SDESProperties]
    ), audit = SDESAudit("file 1"))

  val pendingNotificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020,1,1,1,1),
    updatedAt = LocalDateTime.of(2020,2,2,2,2),
    nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
    notification = notification1
  )

  val sentNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref2", status = RecordStatusEnum.SENT)

  val failedNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref3", status = RecordStatusEnum.PERMANENT_FAILURE)

  "invoke" should {
    "return the count of all records by Status and log them out" in new Setup {
      await(repo.insertFileNotifications(Seq(pendingNotificationRecord, sentNotificationRecord, failedNotificationRecord)))

      withCaptureOfLoggingFrom(logger){
        logs => {
          val result = await(service.invoke)
          result.isRight shouldBe true
          result.right.get shouldBe Seq(
            "[MonitoringJobService][invoke] - Count of Pending Notifications: 1",
            "[MonitoringJobService][invoke] - Count of Sent Notifications: 1",
            "[MonitoringJobService][invoke] - Count of Failed Notifications: 1"
          )
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Pending Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Sent Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Failed Notifications: 1") shouldBe true
        }
      }
    }
  }
}
