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

import base.SpecBase
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.Configuration
import play.api.http.Status
import play.api.test.Helpers._

class BearerTokenControllerSpec extends SpecBase {
  val mockConfig: Configuration = mock[Configuration]

  class Setup {
    reset(mockConfig)
    val controller = new BearerTokenController(stubControllerComponents(), mockConfig)
  }

  "getBearerToken" should {
    s"return OK (${Status.OK}) when the bearer token is in config" in new Setup {
      val serviceName = "fake-service"
      when(mockConfig.getOptional[String](ArgumentMatchers.eq(s"$serviceName.outboundBearerToken"))(any()))
        .thenReturn(Some("token1234"))
      val result = controller.getBearerToken(serviceName)(fakeRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe "token1234"
    }

    s"return NOT_FOUND (${Status.NOT_FOUND}) when the config value is not present" in new Setup {
      val serviceName = "fake-service"
      when(mockConfig.getOptional[String](ArgumentMatchers.eq(s"$serviceName.outboundBearerToken"))(any()))
        .thenReturn(None)
      val result = controller.getBearerToken(serviceName)(fakeRequest)
      status(result) shouldBe NOT_FOUND
      contentAsString(result) shouldBe s"Bearer token not found for service: $serviceName"
    }
  }
}
