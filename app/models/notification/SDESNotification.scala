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

package models.notification

import play.api.libs.json.{JsValue, Json, Reads, Writes}

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
      files <- (json \ "files").validate[SDESNotificationFile]
      audit <- (json \ "audit").validate[SDESAudit]
    } yield {
      SDESNotification(
        informationType,
        files,
        audit
      )
    }
  }
}

case class SDESAudit(correlationID: String)

object SDESAudit {
  implicit val writes: Writes[SDESAudit] = Json.writes[SDESAudit]

  implicit val reads: Reads[SDESAudit] = Json.reads[SDESAudit]
}
