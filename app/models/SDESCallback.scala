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

package models

import play.api.libs.json.{JsValue, Json, Reads}

import java.time.LocalDateTime


case class SDESCallback(
                         notification: SDESFileNotificationEnum.Value,
                         filename: String,
                         checksumAlgorithm: Option[String],
                         checksum: Option[String],
                         correlationID: String,
                         availableUntil: Option[LocalDateTime] = None,
                         failureReason: Option[String],
                         dateTime: Option[LocalDateTime] = None,
                         properties: Option[Seq[Properties]]
                       )
case class Properties(name: String, value: String)

object SDESCallback {
   val apiSDESCallbackReads: Reads[SDESCallback] = Json.reads[SDESCallback]
  (json: JsValue) => {
    for {
      notification <- (json \ "notification").validate[SDESFileNotificationEnum.Value]
      filename <- (json \ "filename").validate[String]
      checksumAlgorithm <- (json \ "checksumAlgorithm").validate[String]
      checksum <- (json \ "checksum").validate[String]
      correlationID <- (json \ "correlationID").validate[String]
      availableUntil <- (json \ "availableUntil").validate[LocalDateTime]
      failureReason <- (json \ "failureReason").validate[String]
      dateTime <- (json \ "dateTime").validate[LocalDateTime]
      properties <-
        (json \ "properties").validate[Properties](
          Properties.formatProperties
        )
    } yield {
      SDESCallback(notification, filename, Some(checksumAlgorithm), Some(checksum), correlationID,
          Some(availableUntil), Some(failureReason), Some(dateTime), Some(Seq(properties)))
    }
  }
}

object Properties {
  implicit val formatProperties: Reads[Properties] = Json.reads[Properties]
}
