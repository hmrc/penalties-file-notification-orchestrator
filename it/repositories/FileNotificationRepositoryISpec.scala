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

package repositories

import java.time.LocalDateTime

import models.SDESNotificationRecord
import models.notification._
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.DeleteResult
import org.scalatest.matchers.should.Matchers._
import play.api.test.Helpers._
import services.NotificationMongoService
import utils.IntegrationSpecCommonBase

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

  val notification1: SDESNotification = SDESNotification(informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "ame", location = "someUrl", checksum = SDESChecksum(algorithm = "sha", value = "256"), size = 256, properties = Seq.empty[SDESProperties]
    ), audit = SDESAudit("file 1"))

  val notification2: SDESNotification = notification1.copy(audit = SDESAudit("File 2"))

  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref",
    status = RecordStatusEnum.SENT,
    numberOfAttempts = 1,
    createdAt = LocalDateTime.of(2020,1,1,1,1),
    updatedAt = LocalDateTime.of(2020,2,2,2,2),
    nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
    notification = notification1
  )

  val notificationRecord2 = notificationRecord.copy(reference = "ref2", notification = notification2)

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
    "get all the notifications with the PENDING status only" in new Setup {
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1),
        updatedAt = LocalDateTime.of(2020,2,2,2,2),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
        notification = notification1
      )
      val notificationRecord2: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref2", status = RecordStatusEnum.PENDING)
      val notificationRecord3: SDESNotificationRecord = notificationRecordInPending.copy(reference = "ref3", status = RecordStatusEnum.SENT)
      await(repository.insertFileNotifications(Seq(notificationRecordInPending, notificationRecord2, notificationRecord3)))
      val result = await(repository.getPendingNotifications())
      result shouldBe Seq(notificationRecordInPending, notificationRecord2)
    }
  }

  "updateFileNotification" should {
    "update the file notification with the new fields for SENT notifications" in new Setup {
      lazy val dateTimeNow: LocalDateTime = LocalDateTime.now()
      val notificationRecordInPending: SDESNotificationRecord = SDESNotificationRecord(
        reference = "ref",
        status = RecordStatusEnum.PENDING,
        numberOfAttempts = 1,
        createdAt = LocalDateTime.of(2020,1,1,1,1),
        updatedAt = LocalDateTime.of(2020,2,2,2,2),
        nextAttemptAt = LocalDateTime.of(2020,3,3,3,3),
        notification = notification1
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
}
