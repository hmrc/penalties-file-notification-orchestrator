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

import java.time.LocalDateTime

import models.notification.{RecordStatusEnum, SDESNotification}
import play.api.libs.json._


case class SDESNotificationRecord(reference: String,
                                    status: RecordStatusEnum.Value = RecordStatusEnum.PENDING,
                                  numberOfAttempts: Int = 0,
                                  createdAt: LocalDateTime = LocalDateTime.now(),
                                  updatedAt: LocalDateTime = LocalDateTime.now(),
                                  nextAttemptAt: LocalDateTime = LocalDateTime.now(),
                                  notification: SDESNotification)

object SDESNotificationRecord {

  implicit val mongoFormats: OFormat[SDESNotificationRecord] = Json.format[SDESNotificationRecord]
}

