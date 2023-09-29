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

package controllers.testOnly

import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.DeleteResult
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.http.Status.OK
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, status}
import repositories.FileNotificationRepository
import utils.IntegrationSpecCommonBase

import scala.concurrent.Future

class MongoQueryControllerISpec extends IntegrationSpecCommonBase {

  val controller: MongoQueryController = injector.instanceOf[MongoQueryController]
  lazy val repository: FileNotificationRepository = injector.instanceOf[FileNotificationRepository]

  def deleteAll(): Future[DeleteResult] =
    repository
      .collection
      .deleteMany(filter = Document())
      .toFuture()

  class Setup {
    await(deleteAll())
  }

  val jsonToReceive: JsValue = Json.parse(
    """
      |[{
      |   "informationType": "type",
      |   "file": {
      |       "recipientOrSender": "recipient",
      |       "name": "file1.txt",
      |       "location": "http://example.com",
      |       "checksum": {
      |           "algorithm": "SHA-256",
      |           "value": "123456789-abcdef-123456789"
      |       },
      |       "size": 1,
      |       "properties": [
      |       {
      |           "name": "name1",
      |           "value": "value1"
      |       }]
      |   },
      |   "audit": {
      |       "correlationID": "12345"
      |   }
      |}]
      |""".stripMargin
  )

  val fakeRequest: FakeRequest[AnyContent] = FakeRequest("GET", "/")

  "getNumberOfRecords" when {
    "return OK and include the correct count of files in repository" in new Setup {
      def mongoRequest: Future[Result] = controller.getNumberOfRecords()(fakeRequest)
      status(mongoRequest) shouldBe OK
      contentAsString(mongoRequest) shouldBe "0"
      val result: WSResponse = await(buildClientForRequestToApp(uri = "/new-notifications").post(
        jsonToReceive
      ))
      result.status shouldBe OK
      status(mongoRequest) shouldBe OK
      contentAsString(mongoRequest) shouldBe "1"
    }
  }
}
