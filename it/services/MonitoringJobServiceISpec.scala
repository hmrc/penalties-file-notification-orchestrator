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

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.duration.DurationInt

class MonitoringJobServiceISpec extends IntegrationSpecCommonBase with LogCapturing {
  class Setup {
    val lockRepository: MongoLockRepository = injector.instanceOf[MongoLockRepository]
    val service: MonitoringJobService = app.injector.instanceOf[MonitoringJobService]
    val repo: FileNotificationRepository = app.injector.instanceOf[FileNotificationRepository]
    await(lockRepository.collection.deleteMany(Document()).toFuture())
    await(lockRepository.ensureIndexes())
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

      await(service.invoke).toOption.get shouldBe Seq(s"${service.jobName} - JobAlreadyRunning")
      await(lockRepository.collection.countDocuments().toFuture()) shouldBe 1
    }
  }

  val pendingNotificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020, 1, 1, 1, 1).toInstant(ZoneOffset.UTC),
    updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2).toInstant(ZoneOffset.UTC),
    nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3).toInstant(ZoneOffset.UTC),
    notification = sampleNotification
  )

  val sentNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref2", status = RecordStatusEnum.SENT)

  val failedNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref3", status = RecordStatusEnum.PERMANENT_FAILURE)

  val failedPendingRetryNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref4", status = RecordStatusEnum.FAILED_PENDING_RETRY)

  val notProcessedPendingRetryNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref5", status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY)

  val fileReceivedInSDESNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref6", status = RecordStatusEnum.FILE_RECEIVED_IN_SDES)

  val fileProcessedInSDESNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref7", status = RecordStatusEnum.FILE_PROCESSED_IN_SDES)

  val fileNotReceivedInSDESNotificationRecord: SDESNotificationRecord = pendingNotificationRecord.copy(reference = "ref8", status = RecordStatusEnum.FILE_NOT_RECEIVED_IN_SDES_PENDING_RETRY)


  "invoke" should {
    "return the count of all records by Status and log them out" in new Setup {
      await(repo.insertFileNotifications(Seq(pendingNotificationRecord, sentNotificationRecord, failedNotificationRecord, failedPendingRetryNotificationRecord, notProcessedPendingRetryNotificationRecord, fileReceivedInSDESNotificationRecord, fileProcessedInSDESNotificationRecord, fileNotReceivedInSDESNotificationRecord)))

      withCaptureOfLoggingFrom(logger){
        logs => {
          val result = await(service.invoke)
          result.isRight shouldBe true
          result.toOption.get shouldBe Seq(
            "[MonitoringJobService][invoke] - Count of Pending Notifications: 1",
            "[MonitoringJobService][invoke] - Count of Sent Notifications: 1",
            "[MonitoringJobService][invoke] - Count of File Received in SDES Notifications: 1",
            "[MonitoringJobService][invoke] - Count of File Not Received in SDES Notifications: 1",
            "[MonitoringJobService][invoke] - Count of File Processed in SDES Notifications: 1",
            "[MonitoringJobService][invoke] - Count of Failed Pending Retry Notifications: 1",
            "[MonitoringJobService][invoke] - Count of Not Processed Pending Retry Notifications: 1",
            "[MonitoringJobService][invoke] - Count of Failed Notifications: 1"
          )
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Pending Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Sent Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of File Received in SDES Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of File Not Received in SDES Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of File Processed in SDES Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Failed Pending Retry Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Not Processed Pending Retry Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Failed Notifications: 1") shouldBe true
        }
      }
    }
  }
}
