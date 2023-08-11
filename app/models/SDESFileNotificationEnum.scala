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

import play.api.libs.json._

object SDESFileNotificationEnum extends Enumeration {

  val FileReady: SDESFileNotificationEnum.Value = Value
  val FileReceived: SDESFileNotificationEnum.Value = Value
  val FileProcessingFailure: SDESFileNotificationEnum.Value = Value
  val FileProcessed: SDESFileNotificationEnum.Value = Value

  implicit val format: Reads[SDESFileNotificationEnum.Value] = new Reads[SDESFileNotificationEnum.Value] {

    override def reads(json: JsValue): JsResult[SDESFileNotificationEnum.Value] = {
      json.as[String].toUpperCase match {
        case "FILEREADY" => JsSuccess(FileReady)
        case "FILERECEIVED" => JsSuccess(FileReceived)
        case "FILEPROCESSINGFAILURE" => JsSuccess(FileProcessingFailure)
        case "FILEPROCESSED" => JsSuccess(FileProcessed)
        case e => JsError(s"$e not recognised")
      }
    }
  }
}
