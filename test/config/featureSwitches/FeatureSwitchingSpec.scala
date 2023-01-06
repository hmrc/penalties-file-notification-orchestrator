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

package config.featureSwitches

import base.SpecBase
import config.AppConfig
import org.mockito.Matchers.any
import org.mockito.Mockito.{mock, reset, when}
import org.scalatest.BeforeAndAfterAll
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class FeatureSwitchingSpec extends SpecBase with BeforeAndAfterAll with FeatureSwitching {
  val mockConfig: Configuration = mock(classOf[Configuration])
  val mockServicesConfig: ServicesConfig = mock(classOf[ServicesConfig])
  val config: AppConfig = new AppConfig(mockConfig, mockServicesConfig)

  class Setup {
    reset(mockConfig)
    reset(mockServicesConfig)
    val featureSwitching: FeatureSwitching = new FeatureSwitching {
      override implicit val appConfig: AppConfig = config
    }
    sys.props -= UseInternalAuth.name
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    sys.props -= UseInternalAuth.name
  }

  "constants" should {
    "be true and false" in new Setup {
      featureSwitching.FEATURE_SWITCH_ON shouldBe "true"
      featureSwitching.FEATURE_SWITCH_OFF shouldBe "false"
    }
  }

  "isEnabled" should {
    s"return true if feature switch is enabled" in new Setup {
      featureSwitching.enableFeatureSwitch(UseInternalAuth)
      featureSwitching.isEnabled(UseInternalAuth) shouldBe true
    }

    s"return false if feature switch is disabled" in new Setup {
      featureSwitching.disableFeatureSwitch(UseInternalAuth)
      featureSwitching.isEnabled(UseInternalAuth) shouldBe false
    }

    "return true if system props is empty but config has value" in new Setup {
      when(mockConfig.get[Boolean](any())(any()))
        .thenReturn(true)
      featureSwitching.isEnabled(UseInternalAuth) shouldBe true
    }
  }

  "enableFeatureSwitch" should {
    s"set ${UseInternalAuth.name} property to true" in new Setup {
      featureSwitching.enableFeatureSwitch(UseInternalAuth)
      sys.props.get(UseInternalAuth.name).get shouldBe "true"
    }
  }

  "disableFeatureSwitch" should {
    s"set ${UseInternalAuth.name} property to false" in new Setup {
      featureSwitching.disableFeatureSwitch(UseInternalAuth)
      sys.props.get(UseInternalAuth.name).get shouldBe "false"
    }
  }
}
