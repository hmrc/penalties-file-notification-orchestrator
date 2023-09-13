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

package utils

import com.codahale.metrics.SharedMetricRegistries
import helpers.WiremockHelper
import models.notification._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}

trait IntegrationSpecCommonBase extends AnyWordSpec with GuiceOneServerPerSuite with
  BeforeAndAfterAll with BeforeAndAfterEach with TestSuite with WiremockHelper {

  lazy val injector: Injector = app.injector

  override def afterEach(): Unit = {
    resetAll()
    stop()
    super.afterEach()
    SharedMetricRegistries.clear()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    start()
    SharedMetricRegistries.clear()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    start()
    SharedMetricRegistries.clear()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    resetAll()
    stop()
    SharedMetricRegistries.clear()
  }

  override lazy val app = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.penalties-stub.host" -> stubHost,
        "microservice.services.penalties-stub.port" -> stubPort,
        "microservice.services.sdes.host" -> stubHost,
        "microservice.services.penalties-stub.port" -> stubPort,
        "notifications.numberOfNotificationsToSendInBatch" -> 10
      )
    )
    .build()

  lazy val ws = app.injector.instanceOf[WSClient]

  def buildClientForRequestToApp(baseUrl: String = "/penalties-file-notification-orchestrator", uri: String): WSRequest = {
    ws.url(s"http://localhost:$port$baseUrl$uri").withFollowRedirects(false)
  }

  val sampleNotification: SDESNotification = SDESNotification(
    informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "file1.txt",
      location = "http://example.com",
      checksum = SDESChecksum(
        algorithm = "SHA-256",
        value = "abcdef-123456789-abcdef"
      ),
      size = 256,
      properties = Seq.empty[SDESProperties]
    ),
    audit = SDESAudit("123456789-abcdefgh-987654321")
  )
}