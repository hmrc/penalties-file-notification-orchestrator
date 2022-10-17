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

import models.SDESNotificationRecord
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.DeleteResult
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.{await, _}
import repositories.FileNotificationRepository
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys
import utils.{IntegrationSpecCommonBase, LogCapturing}

import scala.concurrent.Future

class OrchestratorControllerISpec extends IntegrationSpecCommonBase with LogCapturing {

  val controller: OrchestratorController = injector.instanceOf[OrchestratorController]
  lazy val repository: FileNotificationRepository = injector.instanceOf[FileNotificationRepository]

  def deleteAll(): Future[DeleteResult] =
    repository
      .collection
      .deleteMany(filter = Document())
      .toFuture

  class Setup {
    await(deleteAll())
  }

  val jsonToReceive: JsValue = Json.parse(
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
    "call FileNotificationRepositories - returns OK" in new Setup {
      val result: WSResponse = await(buildClientForRequestToApp(uri = "/new-notifications").post(
        jsonToReceive
      ))
      result.status shouldBe OK
      val recordsInMongoAfterInsertion: Seq[SDESNotificationRecord] = await(repository.collection.find().toFuture)
      recordsInMongoAfterInsertion.size shouldBe 1
      Json.toJson(Seq(recordsInMongoAfterInsertion.head.notification)) shouldBe jsonToReceive
    }

    "return BAD_REQUEST (400)" when {
      "no JSON body is in the request" in new Setup {
        val result: WSResponse = await(buildClientForRequestToApp(uri = "/new-notifications").post(
          ""
        ))
        result.status shouldBe BAD_REQUEST
      }

      "JSON body is present but it can not parsed to a model" in new Setup {
        val result: WSResponse = await(buildClientForRequestToApp(uri = "/new-notifications").post(
          Json.parse("{}")
        ))
        result.status shouldBe BAD_REQUEST
      }
    }

    "return error status code" when {
      "the call to Mongo/stub has a fault" in new Setup {
        val jsonToReceiveWithDuplicateCorrelationID: JsValue = Json.parse(
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
            |},
            |{
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
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result: WSResponse = await(buildClientForRequestToApp(uri = "/new-notifications").post(
              jsonToReceiveWithDuplicateCorrelationID
            ))
            result.status shouldBe INTERNAL_SERVER_ERROR
            eventually {
              logs.exists(_.getMessage.contains(PagerDutyKeys.FAILED_TO_INSERT_SDES_NOTIFICATION))
            }
          }
        }
      }
    }
  }
}
