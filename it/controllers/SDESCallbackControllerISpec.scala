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

package controllers

import org.scalatest.matchers.should.Matchers._
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import utils.IntegrationSpecCommonBase

class SDESCallbackControllerISpec extends IntegrationSpecCommonBase{
  val controller: SDESCallbackController = injector.instanceOf[SDESCallbackController]

  val sdesCallbackJson: JsValue = Json.parse(
    s"""
       |{
       |  "notification": "FileReady",
       |  "filename": "axyz.doc",
       |  "checksumAlgorithm": "MD5",
       |  "checksum": "c6779ec2960296ed9a04f08d67f64422",
       |  "correlationID":"545d0831-d4ba-408d-b1f1-f4645efb32fd",
       |  "availableUntil": "2021-01-06T10:01:00.889Z",
       |  "failureReason": "Virus Detected",
       |  "dateTime": "2021-01-01T10:01:00.889Z",
       |  "properties": [
       |  {
       |    "name": "name1",
       |    "value": "value1"
       |  }]
       |}
       |""".stripMargin
  )

  "handleCallback" should {
    "return NO_CONTENT (204)" when {
      "the callback has a valid JSON body" in {
        val result: WSResponse = await(buildClientForRequestToApp(uri = "/sdes-callback").post(
          sdesCallbackJson
        ))
        result.status shouldBe NO_CONTENT
      }
    }

    "return BAD_REQUEST (400)" when {
      "no JSON body is in the request" in {
        val result: WSResponse = await(buildClientForRequestToApp(uri = "/sdes-callback").post(
          ""
        ))
        result.status shouldBe BAD_REQUEST
      }

      "JSON body is present but it can not parsed to a model" in {
        val result: WSResponse = await(buildClientForRequestToApp(uri = "/sdes-callback").post(
          Json.parse("{}")
        ))
        result.status shouldBe BAD_REQUEST
      }
    }
  }
}
