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

package config

import base.SpecBase
import org.mockito.ArgumentMatchers
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigSpec extends SpecBase {
  val mockConfiguration: Configuration = mock[Configuration]
  val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  class Setup {
    reset(mockConfiguration)
    reset(mockServicesConfig)
  }

  "sdesBaseUrl" should {
    "use the stub URL when the 'useStubForSDESCall' is enabled" in new Setup {
      when(mockConfiguration.get[Boolean](ArgumentMatchers.eq("feature-switch.useStubForSDESCall"))(ArgumentMatchers.any())).thenReturn(true)
      when(mockServicesConfig.baseUrl(ArgumentMatchers.eq("penalties-stub"))).thenReturn("http://penalties-stub")
      val appConfig = new AppConfig(mockConfiguration, mockServicesConfig)
      appConfig.sdesBaseUrl shouldBe "http://penalties-stub/penalties-stub"
    }

    "use the production URL when the 'useStubForSDESCall' is disabled" in new Setup {
      when(mockConfiguration.get[Boolean](ArgumentMatchers.eq("feature-switch.useStubForSDESCall"))(ArgumentMatchers.any())).thenReturn(false)
      when(mockServicesConfig.baseUrl(ArgumentMatchers.eq("sdes"))).thenReturn("http://sdes")
      val appConfig = new AppConfig(mockConfiguration, mockServicesConfig)
      appConfig.sdesBaseUrl shouldBe "http://sdes"
    }
  }
}
