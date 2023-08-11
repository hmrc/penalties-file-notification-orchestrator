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

object RecordStatusEnum extends Enumeration {
  val PENDING: RecordStatusEnum.Value = Value
  val SENT: RecordStatusEnum.Value = Value
  val FILE_RECEIVED_IN_SDES: RecordStatusEnum.Value = Value
  val FILE_NOT_RECEIVED_IN_SDES_PENDING_RETRY: RecordStatusEnum.Value = Value
  val FILE_PROCESSED_IN_SDES: RecordStatusEnum.Value = Value
  val FAILED_PENDING_RETRY: RecordStatusEnum.Value = Value
  val NOT_PROCESSED_PENDING_RETRY: RecordStatusEnum.Value = Value
  val PERMANENT_FAILURE: RecordStatusEnum.Value = Value


  implicit val format: Format[RecordStatusEnum.Value] = new Format[RecordStatusEnum.Value] {
    override def writes(o: RecordStatusEnum.Value): JsValue = {
      JsString(o.toString.toUpperCase)
    }

    private def getEnumFromString(s: String): Option[Value] = values.find(_.toString == s)

    override def reads(json: JsValue): JsResult[RecordStatusEnum.Value] = {
      getEnumFromString(json.as[String].toUpperCase) match {
        case Some(v) => JsSuccess(v)
        case e => JsError(s"$e File status not recognised")
      }
    }
  }
}
