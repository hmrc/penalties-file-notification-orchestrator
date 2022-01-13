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

import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{await, _}
import utils.{IntegrationSpecCommonBase, MockFileNotification}
import org.scalatest.matchers.should.Matchers._

class OrchestratorControllerISpec extends IntegrationSpecCommonBase with MockFileNotification {

  val controller: OrchestratorController = injector.instanceOf[OrchestratorController]


  // TODO: To be implemented when Repositroy is fully implemented
//  "receiveSDESNotifications" should {
//    "call FileNotificationRepositories - returns OK" in {
//      mockFileNotificationStub(OK)
//      val jsonToReceive: JsValue = Json.parse(
//        """
//          |[{
//          |   "informationType": "type",
//          |   "files": {
//          |       "recipientOrSender": "recipient",
//          |       "name": "John Doe",
//          |       "location": "place",
//          |       "checksum": {
//          |           "algorithm": "beep",
//          |           "value": "abc"
//          |       },
//          |       "size": 1,
//          |       "properties": [
//          |       {
//          |           "name": "name",
//          |           "value": "xyz"
//          |       }]
//          |   },
//          |   "audit": {
//          |       "correlationID": "12345"
//          |   }
//          |}]
//          |""".stripMargin
//      )
//      val result = await(buildClientForRequestToApp(uri = "/new-notifications").post(
//        jsonToReceive
//      ))
//      result.status shouldBe OK
//    }
//  }
}
