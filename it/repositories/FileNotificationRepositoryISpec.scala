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

package repositories

import models.SDESNotificationRecord
import models.notification.{RecordStatusEnum, _}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.DeleteResult
import org.scalatest.matchers.should.Matchers._
import play.api.test.Helpers._
import services.NotificationMongoService
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import utils.IntegrationSpecCommonBase

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class FileNotificationRepositoryISpec extends IntegrationSpecCommonBase {
  lazy val repository: FileNotificationRepository = injector.instanceOf[FileNotificationRepository]
  lazy val service: NotificationMongoService = injector.instanceOf[NotificationMongoService]

  def deleteAll(): Future[DeleteResult] =
    repository
      .collection
      .deleteMany(filter = Document())
      .toFuture

  class Setup {
    await(deleteAll())
  }

  val sampleNotification2: SDESNotification = sampleNotification.copy(audit = SDESAudit("987654321-abcdefgh-123456789"))

  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.SENT,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020,1,1,1,1),
    updatedAt = LocalDateTime.of(2020,2,2,2,2),
    nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
    notification = sampleNotification
  )

  val notificationRecord2: SDESNotificationRecord = notificationRecord.copy(reference = "ref2", notification = sampleNotification2)

  "insertFileNotifications" should {
    "insert a single notification received from the backend" in new Setup{
      val result: Boolean = await(repository.insertFileNotifications(Seq(notificationRecord)))
      result shouldBe true
      val recordsInMongoAfterInsertion: Seq[SDESNotificationRecord] = await(repository.collection.find().toFuture)
      recordsInMongoAfterInsertion.size shouldBe 1
      recordsInMongoAfterInsertion.head shouldBe notificationRecord
    }

    "insert multiple notifications received from the backend" in new Setup {
      val result: Boolean = await(repository.insertFileNotifications(Seq(notificationRecord, notificationRecord2)))
      result shouldBe true
      val recordsInMongoAfterInsertion: Seq[SDESNotificationRecord] = await(repository.collection.find().toFuture)
      recordsInMongoAfterInsertion.size shouldBe 2
      recordsInMongoAfterInsertion.head shouldBe notificationRecord
      recordsInMongoAfterInsertion.last shouldBe notificationRecord2
    }
  }

  "getPendingNotifications" should {
    "get all the notifications in PENDING or FAILED_PENDING_RETRY or NOT_PROCESSED_PENDING_RETRY status" in new Setup {
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1),
        updatedAt = LocalDateTime.of(2020,2,2,2,2),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
        notification = sampleNotification
      )
      val notificationRecord2: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref2", status = RecordStatusEnum.PENDING)
      val notificationRecordPendingRetry: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref4", status = RecordStatusEnum.FAILED_PENDING_RETRY)
      val notificationRecordNotProcessedPendingRetry: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref5", status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY)
      val notificationRecord3: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref3", status = RecordStatusEnum.SENT)
      await(repository.insertFileNotifications(Seq(notificationRecordInPending, notificationRecord2, notificationRecord3, notificationRecordPendingRetry,
        notificationRecordNotProcessedPendingRetry)))
      val result = await(repository.getPendingNotifications())
      result shouldBe Seq(notificationRecordPendingRetry, notificationRecordNotProcessedPendingRetry, notificationRecordInPending, notificationRecord2)
    }

    def onlyStatusTest(status: RecordStatusEnum.Value): Unit = {
      s"get all the notifications in $status status (if they are the only records)" in new Setup {
        val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
          reference = "ref",
          status = status,
          numberOfAttempts = 1,
          createdAt = LocalDateTime.of(2020, 1, 1, 1, 1),
          updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2),
          nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3),
          notification = sampleNotification
        )
        val notificationRecord2: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref3", status = RecordStatusEnum.SENT)
        await(repository.insertFileNotifications(Seq(notificationRecordInPending, notificationRecord2)))
        val result = await(repository.getPendingNotifications())
        result shouldBe Seq(notificationRecordInPending)
      }
    }

    onlyStatusTest(RecordStatusEnum.PENDING)
    onlyStatusTest(RecordStatusEnum.FAILED_PENDING_RETRY)
    onlyStatusTest(RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY)
  }

  "updateFileNotification" should {
    "update the file notification with the new fields for SENT notifications" in new Setup {
      lazy val dateTimeNow: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1),
        updatedAt = LocalDateTime.of(2020,2,2,2,2),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
        notification = sampleNotification
      )
      val updatedNotification: SDESNotificationRecord = notificationRecordInPending.copy(
        status = RecordStatusEnum.SENT,
        updatedAt = dateTimeNow
      )
      await(repository.insertFileNotifications(Seq(notificationRecordInPending)))
      await(repository.getPendingNotifications()).size shouldBe 1
      await(repository.updateFileNotification(updatedNotification))
      await(repository.getPendingNotifications()).size shouldBe 0
      await(repository.collection.find(Document()).toFuture()).head shouldBe updatedNotification
    }
  }

  "countRecordsByStatus" should {
    "count all records by their status" in new Setup {
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1),
        updatedAt = LocalDateTime.of(2020,2,2,2,2),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
        notification = sampleNotification
      )

      val notificationRecordInSent: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref1",
        status = RecordStatusEnum.SENT)
      val notificationRecordInFailure: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref2",
        status = RecordStatusEnum.PERMANENT_FAILURE)
      await(repository.insertFileNotifications(Seq(notificationRecordInPending, notificationRecordInSent, notificationRecordInFailure)))
      await(repository.countRecordsByStatus(RecordStatusEnum.PENDING)) shouldBe 1
      await(repository.countRecordsByStatus(RecordStatusEnum.SENT)) shouldBe 1
      await(repository.countRecordsByStatus(RecordStatusEnum.PERMANENT_FAILURE)) shouldBe 1
    }
  }
}
