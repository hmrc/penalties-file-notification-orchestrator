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

package models.monitoring

import models.SDESFileNotificationEnum
import play.api.libs.json.JsValue
import utils.JsonUtils

import java.time.LocalDateTime

case class FileNotificationStatusAuditModel(
                                            fileStatus: SDESFileNotificationEnum.Value,
                                            fileName: String,
                                            correlationId: String,
                                            failureReason: Option[String],
                                            availableUntil: Option[LocalDateTime]
                                           ) extends JsonAuditModel with JsonUtils {
  override val auditType: String = "PenaltyAppealFileNotificationStatus"
  override val detail: JsValue = jsonObjNoNulls(
    "fileStatus" -> fileStatus.toString,
    "fileName" -> fileName,
    "correlationId" -> correlationId,
    "failureReason" -> failureReason,
    "availableUntil" -> availableUntil
  )
  override val transactionName: String = "penalty-appeal-file-notification-status"
}
