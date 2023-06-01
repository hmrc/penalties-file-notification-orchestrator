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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class SDESCallbackSpec extends AnyWordSpec with Matchers {

  val sdesCallbackJson: JsValue = Json.parse(
    """
      |{
      |   "notification": "FileReady",
      |   "filename": "axyz.doc",
      |   "checksumAlgorithm": "MD5",
      |   "checksum": "c6779ec2960296ed9a04f08d67f64422",
      |   "correlationID":"545d0831-d4ba-408d-b1f1-f4645efb32fd",
      |   "availableUntil": "2021-01-06T10:01:00.889Z",
      |   "failureReason": "Virus Detected",
      |   "dateTime": "2021-01-01T10:01:00.889Z",
      |   "properties": [
      |     {
      |       "name": "name1",
      |       "value": "value1"
      |     },
      |     {
      |       "name": "name2",
      |       "value": "value2"
      |     }
      |   ]
      |}
      |""".stripMargin)

  val sdesCallbackJsonWithoutOptionalValues: JsValue = Json.parse(
    """
      |{
      |   "notification": "FileReady",
      |   "filename": "axyz.doc",
      |   "correlationID":"545d0831-d4ba-408d-b1f1-f4645efb32fd",
      |   "dateTime": "2021-01-01T10:01:00.889Z"
      |}
      |""".stripMargin)

  val sdesCallBackModel: SDESCallback = SDESCallback(
    notification = SDESFileNotificationEnum.FileReady,
    filename = "axyz.doc",
    checksumAlgorithm = Some("MD5"),
    checksum = Some("c6779ec2960296ed9a04f08d67f64422"),
    correlationID = "545d0831-d4ba-408d-b1f1-f4645efb32fd",
    availableUntil = Some(LocalDateTime.of(2021, 1, 6, 10, 1, 0).plus(889, ChronoUnit.MILLIS)),
    failureReason = Some("Virus Detected"),
    dateTime = Some(LocalDateTime.of(2021, 1, 1, 10, 1, 0).plus(889, ChronoUnit.MILLIS)),
    properties = Some(Seq(Properties(name = "name1", value = "value1"),
      Properties(name = "name2", value = "value2")))
  )

  val sdesCallBackModelWithRequiredValues: SDESCallback = SDESCallback(
    notification = SDESFileNotificationEnum.FileReady,
    filename = "axyz.doc",
    checksumAlgorithm = None,
    checksum = None,
    correlationID = "545d0831-d4ba-408d-b1f1-f4645efb32fd",
    availableUntil = None,
    failureReason = None,
    dateTime = Some(LocalDateTime.of(2021, 1, 1, 10, 1, 0).plus(889, ChronoUnit.MILLIS)),
    properties = None
  )

  "be readable from JSON" in {
    val result = Json.fromJson(sdesCallbackJson)(SDESCallback.apiSDESCallbackReads)
    result.isSuccess shouldBe true
    result.get shouldBe sdesCallBackModel
  }

  "be readable from JSON when required values are provided" in {
    val result = Json.fromJson(sdesCallbackJsonWithoutOptionalValues)(SDESCallback.apiSDESCallbackReads)
    result.isSuccess shouldBe true
    result.get shouldBe sdesCallBackModelWithRequiredValues
  }

}
