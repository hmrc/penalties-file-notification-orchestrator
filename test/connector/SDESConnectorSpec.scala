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

package connector

import base.SpecBase
import config.AppConfig
import connectors.SDESConnector
import models.notification._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SDESConnectorSpec extends SpecBase {
  val mockHttpClient: HttpClient = mock[HttpClient]
  val mockAppConfig: AppConfig = mock[AppConfig]

  val notification: SDESNotification = SDESNotification(
    informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "name",
      location = "someUrl",
      checksum = SDESChecksum(algorithm = "sha-256", value = "some-sha-256-value"),
      size = 256,
      properties = Seq.empty[SDESProperties]
    ),
    audit = SDESAudit("file 1")
  )

  class Setup {
    reset(mockAppConfig, mockHttpClient)
    val connector: SDESConnector = new SDESConnector(mockAppConfig, mockHttpClient)
  }

  "sendNotificationsToSDES" should {
    "post the notification to the app config value and return the result" in new Setup {
      when(mockAppConfig.sdesUrl).thenReturn("stub/notifications/fileready")
      when(mockAppConfig.sdesOutboundBearerToken).thenReturn("Bearer 12345")
      val hcArgumentCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      when(mockHttpClient.POST[SDESNotification, HttpResponse](ArgumentMatchers.eq("stub/notifications/fileready"),
        ArgumentMatchers.any(),
        ArgumentMatchers.any())
        (ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          hcArgumentCaptor.capture(),
          ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))
      val result: HttpResponse = await(connector.sendNotificationToSDES(notification))
      result.status shouldBe NO_CONTENT
      hcArgumentCaptor.getValue.authorization shouldBe Some(Authorization("Bearer 12345"))
    }
  }
}
