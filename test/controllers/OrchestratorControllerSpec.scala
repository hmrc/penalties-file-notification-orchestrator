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

import base.SpecBase
import config.AppConfig
import config.featureSwitches.UseInternalAuth
import models.notification._
import org.mockito.Matchers
import org.mockito.Mockito.{mock, reset, when}
import org.scalatest.concurrent.Eventually.eventually
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import services.NotificationMongoService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, Retrieval}
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import utils.LogCapturing
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrchestratorControllerSpec extends SpecBase with LogCapturing {
  val mockRepo: FileNotificationRepository = mock(classOf[FileNotificationRepository])
  val mockService: NotificationMongoService = mock(classOf[NotificationMongoService])
  implicit val cc: ControllerComponents = stubControllerComponents()
  val mockAppConfig: AppConfig = mock(classOf[AppConfig])
  lazy val mockAuth: StubBehaviour = mock(classOf[StubBehaviour])
  lazy val authComponent: BackendAuthComponents = BackendAuthComponentsStub(mockAuth)

  class Setup() {
    sys.props -= UseInternalAuth.name
    reset(mockAppConfig)
    reset(mockRepo)
    reset(mockAuth)
    reset(mockService)
    when(mockAuth.stubAuth(Matchers.any(), Matchers.any[Retrieval[Unit]])).thenReturn(Future.unit)
    when(mockAppConfig.isFeatureSwitchEnabled(Matchers.eq(UseInternalAuth))).thenReturn(true)
    val controller = new OrchestratorController(mockService, cc)(implicitly, mockAppConfig, authComponent)
  }

  val notifications: Seq[SDESNotification] = Seq(
    SDESNotification(
      informationType = "type",
      file = SDESNotificationFile(
        recipientOrSender = "recipient",
        name = "John Doe",
        location = "place",
        checksum = SDESChecksum(
          algorithm = "beep",
          value = "abc"
        ),
        size = 1,
        properties = Seq(
          SDESProperties(
            name = "name",
            value = "xyz"
        ))
      ),
      audit = SDESAudit(
        correlationID = "12345"
      )
    )
  )

  val sdesJson: JsValue = Json.parse(
    """
      |[{
      |   "informationType": "type",
      |   "file": {
      |       "recipientOrSender": "recipient",
      |       "name": "John Doe",
      |       "location": "place",
      |       "checksum": {
      |           "algorithm": "beep",
      |           "value": "abc"
      |       },
      |       "size": 1,
      |       "properties": [
      |       {
      |           "name": "name",
      |           "value": "xyz"
      |       }]
      |   },
      |   "audit": {
      |       "correlationID": "12345"
      |   }
      |}]
      |""".stripMargin
  )

  "receiveSDESNotifications" should {
    "return OK (200)" when {
      "the JSON request body is successfully inserted into Mongo" in new Setup {
        when(mockService.insertNotificationRecordsIntoMongo(Matchers.eq(notifications))).thenReturn(Future.successful(true))
        val result: Future[Result] = controller.receiveSDESNotifications()(fakeRequest.withJsonBody(sdesJson))
        status(result) shouldBe OK
      }
    }

    "return BAD_REQUEST (400)" when {
      "the JSON request body is invalid" in new Setup {
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result: Future[Result] = controller.receiveSDESNotifications()(fakeRequest)
            status(result) shouldBe BAD_REQUEST
            contentAsString(result) shouldBe "Invalid body received i.e. could not be parsed to JSON"
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_VALIDATE_REQUEST_AS_JSON.toString)) shouldBe true
            }
          }
        }
      }

      "the request body is valid JSON but can not be serialised to a model" in new Setup {
        val invalidBody: JsValue = Json.parse(
          """
            |[{
            |   "informationType": "type",
            |   "file": {
            |       "recipientOrSender": "recipient",
            |       "name": "John Doe",
            |       "location": "place"
            |   }
            |}]
            |""".stripMargin
        )
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result: Future[Result] = controller.receiveSDESNotifications()(fakeRequest.withJsonBody(invalidBody))
            status(result) shouldBe BAD_REQUEST
            contentAsString(result) shouldBe "Failed to parse to model"
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PARSE_REQUEST_TO_MODEL.toString)) shouldBe true
            }
          }
        }
      }
    }

    "return INTERNAL_SERVER_ERROR (500)" when {
      "repository fails to insert File Notification" in new Setup {
        withCaptureOfLoggingFrom(logger) {
          logs => {
            when(mockService.insertNotificationRecordsIntoMongo(Matchers.eq(notifications))).thenReturn(Future.successful(false))
            val result: Future[Result] = controller.receiveSDESNotifications()(fakeRequest.withJsonBody(sdesJson))
            status(result) shouldBe INTERNAL_SERVER_ERROR
            contentAsString(result) shouldBe "Failed to insert File Notifications"
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_INSERT_FILE_NOTIFICATION.toString)) shouldBe true
            }
          }
        }
      }

      "an error is thrown" in new Setup {

        withCaptureOfLoggingFrom(logger) {
          logs => {
            when(mockService.insertNotificationRecordsIntoMongo(Matchers.eq(notifications))).thenReturn(Future.failed(new Exception("ERROR")))
            val result: Future[Result] = controller.receiveSDESNotifications()(fakeRequest.withJsonBody(sdesJson))
            status(result) shouldBe INTERNAL_SERVER_ERROR
            contentAsString(result) shouldBe "Something went wrong."
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.UNKNOWN_EXCEPTION_FROM_SDES.toString)) shouldBe true
            }
          }
        }
      }
    }

    "return UNAUTHORIZED (401)" when {
      "no authentication has been provided" in new Setup {
        val result: Future[Result] = controller.receiveSDESNotifications()(FakeRequest("GET", "/").withJsonBody(sdesJson))
        status(result) shouldBe UNAUTHORIZED
      }
    }

    "return FORBIDDEN (403)" when {
      "the calling service is not authenticated" in new Setup {
        when(mockAuth.stubAuth(Matchers.any(), Matchers.any())).thenReturn(Future.failed(UpstreamErrorResponse("FORBIDDEN", FORBIDDEN)))
        val result: Future[Result] = controller.receiveSDESNotifications()(fakeRequest.withJsonBody(sdesJson))
        status(result) shouldBe FORBIDDEN
      }
    }
  }
}
