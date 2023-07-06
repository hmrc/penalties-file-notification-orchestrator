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
import models.SDESFileNotificationEnum._
import models.notification.RecordStatusEnum._
import models.{Properties, SDESCallback, SDESFileNotificationEnum}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.Eventually.eventually
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NO_CONTENT}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status, stubControllerComponents}
import services.HandleCallbackService
import services.monitoring.AuditService
import utils.LogCapturing
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys
import utils.PagerDutyHelper.PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SDESCallbackControllerSpec extends SpecBase with LogCapturing {
  val mockAuditService: AuditService = mock[AuditService]
  val mockHandleCallbackService: HandleCallbackService = mock[HandleCallbackService]

  class Setup {
    reset(mockAuditService, mockHandleCallbackService)
    val sdesCallbackController = new SDESCallbackController(mockAuditService, mockHandleCallbackService, stubControllerComponents())
  }

  val sdesCallbackModel: SDESCallback = SDESCallback(
      notification = SDESFileNotificationEnum.FileReady,
      filename = "axyz.doc",
      checksumAlgorithm = Some("SHA-256"),
      checksum = Some("c6779ec2960296ed9a04f08d67f64422"),
      correlationID = "545d0831-d4ba-408d-b1f1-f4645efb32fd",
      availableUntil = Some(LocalDateTime.of(2021, 1, 6, 10, 1, 0).plus(889, ChronoUnit.MILLIS)),
      failureReason = Some("Virus Detected"),
      dateTime = Some(LocalDateTime.of(2021, 1, 1, 10, 1, 0).plus(889, ChronoUnit.MILLIS)),
      properties = Some(Seq(Properties("name1", "value1")))
    )

  val sdesCallbackJson: JsObject = Json.obj(
    "notification" -> "FileReady",
    "filename" -> "axyz.doc",
    "checksumAlgorithm" -> "SHA-256",
    "checksum" -> "c6779ec2960296ed9a04f08d67f64422",
    "correlationID" -> "545d0831-d4ba-408d-b1f1-f4645efb32fd",
    "availableUntil" -> "2021-01-06T10:01:00.889Z",
    "failureReason" -> "Virus Detected",
    "dateTime" -> "2021-01-01T10:01:00.889Z",
    "properties" -> Json.arr(
      Json.obj("name" -> "name1", "value" -> "value1")
  ))

  "handleCallback" should {
    s"return NO_CONTENT ($NO_CONTENT)" when {
      s"the JSON request body is valid - setting to $FILE_RECEIVED_IN_SDES when '$FileReceived' is returned" in new Setup {
        when(mockHandleCallbackService.updateNotificationAfterCallback(any(), ArgumentMatchers.eq(FILE_RECEIVED_IN_SDES))).thenReturn(Future.successful(Right((): Unit)))
        val result: Future[Result] = sdesCallbackController.handleCallback()(fakeRequest.withJsonBody(sdesCallbackJson ++ Json.obj("notification" -> FileReceived)))
        verify(mockAuditService)
          .audit(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        status(result) shouldBe NO_CONTENT
      }

      s"the JSON request body is valid - setting to $FILE_PROCESSED_IN_SDES when '$FileReady' is returned" in new Setup {
        when(mockHandleCallbackService.updateNotificationAfterCallback(any(), ArgumentMatchers.eq(FILE_PROCESSED_IN_SDES))).thenReturn(Future.successful(Right((): Unit)))
        val result: Future[Result] = sdesCallbackController.handleCallback()(fakeRequest.withJsonBody(sdesCallbackJson ++ Json.obj("notification" -> FileReady)))
        verify(mockAuditService)
          .audit(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        status(result) shouldBe NO_CONTENT
      }

      s"the JSON request body is valid - setting to $FILE_PROCESSED_IN_SDES when '$FileProcessed' is returned" in new Setup {
        when(mockHandleCallbackService.updateNotificationAfterCallback(any(), ArgumentMatchers.eq(FILE_PROCESSED_IN_SDES))).thenReturn(Future.successful(Right((): Unit)))
        val result: Future[Result] = sdesCallbackController.handleCallback()(fakeRequest.withJsonBody(sdesCallbackJson ++ Json.obj("notification" -> FileProcessed)))
        verify(mockAuditService)
          .audit(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
        status(result) shouldBe NO_CONTENT
      }

      s"the JSON request body is valid - setting to $FAILED_PENDING_RETRY when '$FileProcessingFailure' is returned (logging a PD)" in new Setup {
        when(mockHandleCallbackService.updateNotificationAfterCallback(any(), ArgumentMatchers.eq(FAILED_PENDING_RETRY))).thenReturn(Future.successful(Right((): Unit)))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result: Future[Result] = sdesCallbackController.handleCallback()(fakeRequest.withJsonBody(sdesCallbackJson ++ Json.obj("notification" -> FileProcessingFailure)))
            verify(mockAuditService)
              .audit(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
            status(result) shouldBe NO_CONTENT
            logs.exists(_.getMessage.contains(FAILED_TO_PROCESS_FILE_NOTIFICATION.toString)) shouldBe true
          }
        }
      }
    }

    s"return BAD_REQUEST ($BAD_REQUEST)" when {
      "the JSON request body is invalid " in new Setup {
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result: Future[Result] = sdesCallbackController.handleCallback()(fakeRequest)
            status(result) shouldBe BAD_REQUEST
            contentAsString(result) shouldBe "Invalid body received i.e. could not be parsed to JSON"
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_VALIDATE_REQUEST_AS_JSON.toString)) shouldBe true
            }
          }
        }
      }

      "the SDES callback JSON body is valid but can not be serialised to a model" in new Setup {
        val invalidBody: JsValue = Json.parse(
          s"""
            |[{
            |   "notification": "FileReady",
            |   "filename": "axyz.doc",
            |   "checksumAlgorithm": "MD5",
            |   "checksum": "c6779ec2960296ed9a04f08d67f64422",
            |   "correlationID":"545d0831-d4ba-408d-b1f1-f4645efb32fd",
            |   "availableUntil": "2021-01-06T10:01:00.889Z",
            |   "properties": [
            |   {
            |     "name": "name1"
            |   }]
            |}]
            |""".stripMargin
        )
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result: Future[Result] = sdesCallbackController.handleCallback()(fakeRequest.withJsonBody(invalidBody))
            status(result) shouldBe BAD_REQUEST
            contentAsString(result) shouldBe "Failed to parse to model"
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_PARSE_REQUEST_TO_MODEL.toString)) shouldBe true
            }
          }
        }
      }
    }
  }

  s"return ISE ($INTERNAL_SERVER_ERROR)" when {
    "the notification fails to be updated in Mongo" in new Setup {
      when(mockHandleCallbackService.updateNotificationAfterCallback(any(), any())).thenReturn(Future.successful(Left("Something went wrong")))
      val result: Future[Result] = sdesCallbackController.handleCallback()(fakeRequest.withJsonBody(sdesCallbackJson))
      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
