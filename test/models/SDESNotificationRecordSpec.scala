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

package models

import models.notification._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText}

import java.time.Instant

class SDESNotificationRecordSpec extends AnyWordSpec with Matchers {

  val modelAsJson: JsValue = Json.parse(
    s"""
      |{
      | "reference": "ref123",
      | "status": "PENDING",
      | "numberOfAttempts": 0,
      | "createdAt": ${Json.toJson(Instant.parse("2023-09-01T12:00:00.000Z"))(SDESNotificationRecord.instantFormat)},
      | "updatedAt": ${Json.toJson(Instant.parse("2023-09-01T12:00:00.000Z"))(SDESNotificationRecord.instantFormat)},
      | "nextAttemptAt": ${Json.toJson(Instant.parse("2023-09-01T13:00:00.000Z"))(SDESNotificationRecord.instantFormat)},
      | "notification": {
      |   "informationType": "info1",
      |   "file": {
      |     "recipientOrSender": "recipient1",
      |     "name": "file1.txt",
      |     "location": "http://example.com/file1.txt",
      |     "checksum": {
      |       "algorithm": "SHA-256",
      |       "value": "this is the checksum"
      |     },
      |     "size": 1,
      |     "properties": []
      |   },
      |   "audit": {
      |     "correlationID": "corr-id-1"
      |   }
      | }
      |}
      |""".stripMargin)

  val file: SDESNotificationFile = SDESNotificationFile(
    recipientOrSender = "recipient1",
    name = "file1.txt",
    location = "http://example.com/file1.txt",
    checksum = SDESChecksum(
      algorithm = "SHA-256",
      value = "this is the checksum"
    ),
    size = 1,
    properties = Seq.empty
  )

  val model: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref123",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 0,
    createdAt = Instant.parse("2023-09-01T12:00:00.000Z"),
    updatedAt = Instant.parse("2023-09-01T12:00:00.000Z"),
    nextAttemptAt = Instant.parse("2023-09-01T13:00:00.000Z"),
    notification = SDESNotification(
      informationType = "info1",
      file = file,
      audit = SDESAudit(correlationID = "corr-id-1")
    )
  )

  "be readable from JSON" in {
    val result = Json.fromJson(modelAsJson)(SDESNotificationRecord.mongoFormats)
    result.isSuccess shouldBe true
    result.get shouldBe model
  }

  "be writable to JSON" in {
    val result = Json.toJson(model)(SDESNotificationRecord.mongoFormats)
    result shouldBe modelAsJson
  }

  "encrypt" should {
    "encrypt the file name and location" in {
      val fakeEncrypter = new Encrypter {
        override def encrypt(plain: PlainContent): Crypted = Crypted("encrypted value")
      }
      model.notification.file.name shouldBe "file1.txt"
      model.notification.file.location shouldBe "http://example.com/file1.txt"
      val result = SDESNotificationRecord.encrypt(model)(fakeEncrypter)
      result.notification.file.name shouldBe "encrypted value"
      result.notification.file.location shouldBe "encrypted value"
    }
  }

  "decrypt" should {
    "decrypt the file name and location" in {
      val fakeDecrypter = new Decrypter {
        override def decrypt(reversiblyEncrypted: Crypted): PlainText = PlainText("decrypted value")

        override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = PlainBytes(new Array[Byte](1))
      }
      val modelWithEncryptedFields = model.copy(
        notification = model.notification.copy(
          file = file.copy(
          name = "encrypted name value",
          location = "encrypted location value"
          )
        )
      )
      modelWithEncryptedFields.notification.file.name shouldBe "encrypted name value"
      modelWithEncryptedFields.notification.file.location shouldBe "encrypted location value"
      val result = SDESNotificationRecord.decrypt(model)(fakeDecrypter)
      result.notification.file.name shouldBe "decrypted value"
      result.notification.file.location shouldBe "decrypted value"
    }
  }
}
