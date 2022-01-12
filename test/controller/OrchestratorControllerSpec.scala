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

package controller

import base.SpecBase
import config.AppConfig
import controllers.OrchestratorController
import models.notification.{SDESAudit, SDESChecksum, SDESNotification, SDESNotificationFile, SDESProperties}
import org.mockito.Mockito.{mock, reset, when}
import play.api.http.Status
import play.api.test.Helpers._
import repositories.FileNotificationRepositories
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.http.HttpResponse

import scala.collection.convert.Wrappers.SeqWrapper
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrchestratorControllerSpec extends SpecBase {
  val mockRepo: FileNotificationRepositories = mock(classOf[FileNotificationRepositories])
  val mockAppConfig: AppConfig = mock(classOf[AppConfig])

  class Setup() {
    reset(mockAppConfig)
    reset(mockRepo)
    val controller = new OrchestratorController(mockRepo, stubControllerComponents())
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

  "receiveSIDESNotifications" should {
    s"return OK (${Status.OK})" in new Setup {
      when(mockRepo.storeFileNotifications(notifications)).thenReturn(Future.successful(HttpResponse(OK, "")))
      val sdesJson: JsValue = Json.parse(
        """
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
          |}
          |""".stripMargin
      )
      val result: Future[Result] = controller.receiveSDESNotifications()(fakeRequest.withJsonBody(sdesJson))
      status(result) shouldBe OK
    }
  }
}
