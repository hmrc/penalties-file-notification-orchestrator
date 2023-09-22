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

import models.notification.{RecordStatusEnum, SDESNotification, SDESNotificationFile}
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class SDESNotificationRecord(reference: String,
                                  status: RecordStatusEnum.Value = RecordStatusEnum.PENDING,
                                  numberOfAttempts: Int = 0,
                                  createdAt: Instant = Instant.now(),
                                  updatedAt: Instant = Instant.now(),
                                  nextAttemptAt: Instant = Instant.now(),
                                  notification: SDESNotification
                                 )

object SDESNotificationRecord extends MongoJavatimeFormats {
  implicit val dateFormat: Format[Instant] = instantFormat
  implicit val mongoFormats: OFormat[SDESNotificationRecord] = Json.format[SDESNotificationRecord]

  private def encryptFile(file: SDESNotificationFile)(implicit encrypter: Encrypter): SDESNotificationFile = {
    file.copy(
      name = encrypter.encrypt(PlainText(file.name)).value,
      location = encrypter.encrypt(PlainText(file.location)).value
    )
  }

  private def decryptFile(file: SDESNotificationFile)(implicit decrypter: Decrypter): SDESNotificationFile = {
    file.copy(
      name = decrypter.decrypt(Crypted(file.name)).value,
      location = decrypter.decrypt(Crypted(file.location)).value
    )
  }

  def encrypt(record: SDESNotificationRecord)(implicit encrypter: Encrypter): SDESNotificationRecord = {
    val encryptedFile = encryptFile(record.notification.file)
    record.copy(
      notification = record.notification.copy(file = encryptedFile)
    )
  }

  def decrypt(record: SDESNotificationRecord)(implicit decrypter: Decrypter): SDESNotificationRecord = {
    val decryptedFile = decryptFile(record.notification.file)
    record.copy(
      notification = record.notification.copy(file = decryptedFile)
    )
  }
}

