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

package models.notification

import play.api.libs.json._

case class SDESNotification(
                             informationType: String,
                             file: SDESNotificationFile,
                             audit: SDESAudit
                           )

object SDESNotification {
  implicit val writes: Writes[SDESNotification] = Json.writes[SDESNotification]

  val apiReads: Reads[SDESNotification] = (json: JsValue) => {
    for {
      informationType <- (json \ "informationType").validate[String]
      file <- (json \ "file").validate[SDESNotificationFile]
      audit <- (json \ "audit").validate[SDESAudit]
    } yield {
      SDESNotification(
        informationType,
        file,
        audit
      )
    }
  }

  implicit val mongoFormats: OFormat[SDESNotification] = Json.format[SDESNotification]
}

case class SDESAudit(correlationID: String)

object SDESAudit {

  implicit val mongoFormats: OFormat[SDESAudit] = Json.format[SDESAudit]
}
