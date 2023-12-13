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
import models.notification._
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.DeleteResult
import org.scalatest.matchers.should.Matchers._
import play.api.test.Helpers._
import services.NotificationMongoService
import utils.IntegrationSpecCommonBase

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneId, ZoneOffset}
import scala.concurrent.Future

class FileNotificationRepositoryISpec extends IntegrationSpecCommonBase {
  lazy val repository: FileNotificationRepository = injector.instanceOf[FileNotificationRepository]
  lazy val service: NotificationMongoService = injector.instanceOf[NotificationMongoService]

  def deleteAll(): Future[DeleteResult] =
    repository
      .collection
      .deleteMany(filter = Document())
      .toFuture()

  class Setup {
    await(deleteAll())
  }

  val sampleNotification2: SDESNotification = sampleNotification.copy(audit = SDESAudit("987654321-abcdefgh-123456789"))

  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.SENT,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020,1,1,1,1).toInstant(ZoneOffset.UTC),
    updatedAt = LocalDateTime.of(2020,2,2,2,2).toInstant(ZoneOffset.UTC),
    nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC),
    notification = sampleNotification
  )

  val notificationRecord2: SDESNotificationRecord = notificationRecord.copy(reference = "ref2", notification = sampleNotification2)

  "insertFileNotifications" should {
    "insert a single notification received from the backend" in new Setup{
      val result: Boolean = await(repository.insertFileNotifications(Seq(notificationRecord)))
      result shouldBe true
      val recordsInMongoAfterInsertion: Seq[SDESNotificationRecord] = await(repository.collection.find().map(SDESNotificationRecord.decrypt(_)).toFuture())
      recordsInMongoAfterInsertion.size shouldBe 1
      recordsInMongoAfterInsertion.head shouldBe notificationRecord
    }

    "insert multiple notifications received from the backend" in new Setup {
      val result: Boolean = await(repository.insertFileNotifications(Seq(notificationRecord, notificationRecord2)))
      result shouldBe true
      val recordsInMongoAfterInsertion: Seq[SDESNotificationRecord] = await(repository.collection.find().map(SDESNotificationRecord.decrypt(_)).toFuture())
      recordsInMongoAfterInsertion.size shouldBe 2
      recordsInMongoAfterInsertion.head shouldBe notificationRecord
      recordsInMongoAfterInsertion.last shouldBe notificationRecord2
    }
  }

  "getPendingNotifications" should {
    "get all the notifications in PENDING, FAILED_PENDING_RETRY, NOT_PROCESSED_PENDING_RETRY or FILE_NOT_RECEIVED_IN_SDES_PENDING_RETRY status" in new Setup {
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020,2,2,2,2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC),
        notification = sampleNotification
      )
      val notificationRecord2: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref2", status = RecordStatusEnum.PENDING)
      val notificationRecordPendingRetry: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref4", status = RecordStatusEnum.FAILED_PENDING_RETRY)
      val notificationRecordNotProcessedPendingRetry: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref5", status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY)
      val notificationRecord3: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref3", status = RecordStatusEnum.SENT)
      val notificationRecord6: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref6", status = RecordStatusEnum.FILE_NOT_RECEIVED_IN_SDES_PENDING_RETRY)

      await(repository.insertFileNotifications(Seq(notificationRecordInPending, notificationRecord2, notificationRecord3, notificationRecord6,
        notificationRecordPendingRetry, notificationRecordNotProcessedPendingRetry)))
      val result = await(repository.getPendingNotifications())
      result shouldBe Seq(notificationRecordPendingRetry, notificationRecord6, notificationRecordNotProcessedPendingRetry, notificationRecordInPending, notificationRecord2)
    }

    def onlyStatusTest(status: RecordStatusEnum.Value): Unit = {
      s"get all the notifications in $status status (if they are the only records)" in new Setup {
        val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
          reference = "ref",
          status = status,
          numberOfAttempts = 1,
          createdAt = LocalDateTime.of(2020, 1, 1, 1, 1).toInstant(ZoneOffset.UTC),
          updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2).toInstant(ZoneOffset.UTC),
          nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3).toInstant(ZoneOffset.UTC),
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
        createdAt = LocalDateTime.of(2020,1,1,1,1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020,2,2,2,2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC),
        notification = sampleNotification
      )
      val updatedNotification: SDESNotificationRecord = notificationRecordInPending.copy(
        status = RecordStatusEnum.SENT,
        updatedAt = dateTimeNow.toInstant(ZoneOffset.UTC)
      )
      await(repository.insertFileNotifications(Seq(notificationRecordInPending)))
      await(repository.getPendingNotifications()).size shouldBe 1
      await(repository.updateFileNotification(updatedNotification))
      await(repository.getPendingNotifications()).size shouldBe 0
      await(repository.collection.find().map(SDESNotificationRecord.decrypt(_)).toFuture()).head shouldBe updatedNotification
    }
  }

  "countRecordsByStatus" should {
    "count all records by their status" in new Setup {
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020,2,2,2,2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC),
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

  "updateFileNotification(reference, updatedStatus)" should {
    "find the existing record and update the fields (not incrementing retries when successful) when not in FILE_PROCESSED_IN_SDES state" in new Setup {
      val notificationRecordInSent: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.SENT,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020, 1, 1, 1, 1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3).toInstant(ZoneOffset.UTC),
        notification = sampleNotification
      )
      await(repository.insertFileNotifications(Seq(notificationRecordInSent)))
      await(repository.updateFileNotification("ref", RecordStatusEnum.FILE_PROCESSED_IN_SDES))
      val updatedNotification: SDESNotificationRecord = await(repository.collection.find().toFuture()).head
      updatedNotification.reference shouldBe notificationRecordInSent.reference
      updatedNotification.status shouldBe RecordStatusEnum.FILE_PROCESSED_IN_SDES
      updatedNotification.numberOfAttempts shouldBe 1
      updatedNotification.createdAt shouldBe notificationRecordInSent.createdAt
      updatedNotification.updatedAt.isAfter(notificationRecordInSent.updatedAt) shouldBe true
      LocalDateTime.ofInstant(updatedNotification.nextAttemptAt, ZoneId.of("UTC")).withSecond(0).withNano(0) shouldBe LocalDateTime.now(ZoneId.of("UTC")).withSecond(0).withNano(0)
    }

    "find the existing record and not update the fields when in FILE_PROCESSED_IN_SDES state" in new Setup {
      val notificationRecordInProcessed: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.FILE_PROCESSED_IN_SDES,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020, 1, 1, 1, 1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3).toInstant(ZoneOffset.UTC),
        notification = sampleNotification
      )
      await(repository.insertFileNotifications(Seq(notificationRecordInProcessed)))
      await(repository.updateFileNotification("ref", RecordStatusEnum.FAILED_PENDING_RETRY))

      val updatedNotification: SDESNotificationRecord = await(repository.collection.find().toFuture()).head
      updatedNotification.reference shouldBe notificationRecordInProcessed.reference
      updatedNotification.status shouldBe RecordStatusEnum.FILE_PROCESSED_IN_SDES
      updatedNotification.numberOfAttempts shouldBe 1
      updatedNotification.createdAt shouldBe notificationRecordInProcessed.createdAt
      updatedNotification.updatedAt.isAfter(notificationRecordInProcessed.updatedAt) shouldBe false
    }

    "find the existing record and update the fields (incrementing retries when failed - NOT_PROCESSED_PENDING_RETRY)" in new Setup {
      val notificationRecordInSent: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.SENT,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020, 1, 1, 1, 1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3).toInstant(ZoneOffset.UTC),
        notification = sampleNotification
      )
      await(repository.insertFileNotifications(Seq(notificationRecordInSent)))
      await(repository.updateFileNotification("ref", RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY))
      val updatedNotification: SDESNotificationRecord = await(repository.collection.find().toFuture()).head
      updatedNotification.reference shouldBe notificationRecordInSent.reference
      updatedNotification.status shouldBe RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY
      updatedNotification.numberOfAttempts shouldBe 2
      updatedNotification.createdAt shouldBe notificationRecordInSent.createdAt
      updatedNotification.updatedAt.isAfter(notificationRecordInSent.updatedAt) shouldBe true
      LocalDateTime.ofInstant(updatedNotification.nextAttemptAt, ZoneId.of("UTC")).withSecond(0).withNano(0) shouldBe LocalDateTime.now(ZoneId.of("UTC")).plusMinutes(30).withSecond(0).withNano(0)
    }

    "find the existing record and update the fields (incrementing retries when failed - FAILED_PENDING_RETRY)" in new Setup {
      val notificationRecordInSent: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.SENT,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020, 1, 1, 1, 1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3).toInstant(ZoneOffset.UTC),
        notification = sampleNotification
      )
      await(repository.insertFileNotifications(Seq(notificationRecordInSent)))
      await(repository.updateFileNotification("ref", RecordStatusEnum.FAILED_PENDING_RETRY))
      val updatedNotification: SDESNotificationRecord = await(repository.collection.find().toFuture()).head
      updatedNotification.reference shouldBe notificationRecordInSent.reference
      updatedNotification.status shouldBe RecordStatusEnum.FAILED_PENDING_RETRY
      updatedNotification.numberOfAttempts shouldBe 2
      updatedNotification.createdAt shouldBe notificationRecordInSent.createdAt
      updatedNotification.updatedAt.isAfter(notificationRecordInSent.updatedAt) shouldBe true
      LocalDateTime.ofInstant(updatedNotification.nextAttemptAt, ZoneId.of("UTC")).withSecond(0).withNano(0) shouldBe LocalDateTime.now(ZoneId.of("UTC")).plusMinutes(30).withSecond(0).withNano(0)
    }
  }

  "getNotificationsInState" should {
    s"return correct number of records when called" in new Setup {
      val notification = SDESNotificationRecord(
        "ref1", RecordStatusEnum.PENDING, notification = sampleNotification)
      val notificationsWithFileReceived = Seq(
        notification, notification.copy(reference = "ref2", status = RecordStatusEnum.SENT),
        notification.copy("ref3", status = RecordStatusEnum.FAILED_PENDING_RETRY),
        notification.copy("ref4", status = RecordStatusEnum.SENT))


      await(repository.insertFileNotifications(notificationsWithFileReceived))
      val result = await(repository.getNotificationsInState(RecordStatusEnum.SENT))
      result.size shouldBe 2
      result.exists(_.reference.equals("ref2")) shouldBe true
      result.exists(_.reference.equals("ref4")) shouldBe true
    }
  }

  "countAllRecords" should {
    "return all records in repository" in new Setup {
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1).toInstant(ZoneOffset.UTC),
        updatedAt = LocalDateTime.of(2020,2,2,2,2).toInstant(ZoneOffset.UTC),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3).toInstant(ZoneOffset.UTC),
        notification = sampleNotification
      )

      val notificationRecordInSent: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref1",
        status = RecordStatusEnum.SENT)
      val notificationRecordInFailure: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref2",
        status = RecordStatusEnum.PERMANENT_FAILURE)
      await(repository.insertFileNotifications(Seq(notificationRecordInPending, notificationRecordInSent, notificationRecordInFailure)))
      await(repository.countAllRecords()) shouldBe 3
    }
  }
}
