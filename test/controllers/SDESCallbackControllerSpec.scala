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

package controllers

import base.SpecBase
import models.{Properties, SDESCallback, SDESFileNotificationEnum}
import org.mockito.Mockito.mock
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status, stubControllerComponents}
import services.monitoring.AuditService

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SDESCallbackControllerSpec extends SpecBase {
  val mockService: AuditService = mock(classOf[AuditService])

  class Setup() {
    val sdesController = new SDESCallbackController(mockService, stubControllerComponents())
  }

  val sdesCallbackModel: SDESCallback = SDESCallback(SDESFileNotificationEnum.FileReady,"axyz.doc",Some("MD5"),Some("c6779ec2960296ed9a04f08d67f64422"), "545d0831-d4ba-408d-b1f1-f4645efb32fd",
    Some(LocalDateTime.of(2021, 1, 6, 10, 1, 0).plus(889, ChronoUnit.MILLIS)),
    Some("Virus Detected"), Some(LocalDateTime.of(2021, 1, 1, 10, 1, 0).plus(889, ChronoUnit.MILLIS)),
    Some(Seq(Properties("name1","value1"))))

  val sdesCallbackJson: JsValue = Json.parse(
    s"""
       | {
       |            "notification": "FileReady",
       |            "filename": "axyz.doc",
       |            "checksumAlgorithm": "MD5",
       |            "checksum": "c6779ec2960296ed9a04f08d67f64422",
       |            "correlationID":"545d0831-d4ba-408d-b1f1-f4645efb32fd",
       |            "availableUntil": "2021-01-06T10:01:00.889Z",
       |            "failureReason": "Virus Detected",
       |            "dateTime": "2021-01-01T10:01:00.889Z",
       |            "properties": [
       |                {
       |                    "name": "name1",
       |                    "value": "value1"
       |                }
       |            ]
       |        }
       |""".stripMargin
  )

  "receiveSDESCallback" should {
    "return content string for Valid sdesCallback JSON Body" when {
      "the JSON request body is valid" in new Setup {
        val result: Future[Result] = sdesController.handleCallback()(fakeRequest.withJsonBody(sdesCallbackJson))
         contentAsString(result) shouldBe "Valid SDESCallback body"
      }
    }

    "return content string for Invalid sdesCallback JSON Body" when {
      "the JSON request body is invalid " in new Setup {
        val result: Future[Result] = sdesController.handleCallback()(fakeRequest)
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid body received i.e. could not be parsed to JSON"
      }

      "the sdesCallback JSON body is valid but can not be serialised to a model" in new Setup {
        val invalidBody: JsValue = Json.parse(
          s"""
            |[{
            |    "notification": "FileReady",
            |     "filename": "axyz.doc",
            |     "checksumAlgorithm": "MD5",
            |     "checksum": "c6779ec2960296ed9a04f08d67f64422",
            |      "correlationID":"545d0831-d4ba-408d-b1f1-f4645efb32fd",
            |      "availableUntil": "2021-01-06T10:01:00.889Z",
            |      "properties": [
            |                {
            |                    "name": "name1"
            |                }
            |            ]
            |}]
            |""".stripMargin
        )
        val result: Future[Result] = sdesController.handleCallback()(fakeRequest.withJsonBody(invalidBody))
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Failed to parse to model"
      }
    }
  }
}
