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
import org.mockito.Matchers
import org.mockito.Mockito.{mock, when}

class FeatureSwitchSpec extends SpecBase {
  val mockAppConfig: AppConfig = mock(classOf[AppConfig])

  class Setup {
    val featureSwitching: FeatureSwitching = new FeatureSwitching {
      override implicit val appConfig: AppConfig = mockAppConfig
    }
    sys.props -= UseInternalAuth.name
  }

  "listOfAllFeatureSwitches" should {
    "be all the featureswitches in the app" in {
      FeatureSwitch.listOfAllFeatureSwitches shouldBe List(UseInternalAuth)
    }
  }

  "constants" should {
    "be true and false" in new Setup {
      featureSwitching.FEATURE_SWITCH_ON shouldBe "true"
      featureSwitching.FEATURE_SWITCH_OFF shouldBe "false"
    }
  }

  "return true if UseInternalAuth feature switch is enabled" in new Setup {
    featureSwitching.enableFeatureSwitch(UseInternalAuth)
    featureSwitching.isEnabled(UseInternalAuth) shouldBe true
  }

  "return false if UseInternalAuth feature switch is disabled" in new Setup {
    featureSwitching.disableFeatureSwitch(UseInternalAuth)
    featureSwitching.isEnabled(UseInternalAuth) shouldBe false
  }

  "return true if UseInternalAuth feature switch does not exist in cache but does in config" in new Setup {
    when(mockAppConfig.isFeatureSwitchEnabled(Matchers.eq(UseInternalAuth)))
      .thenReturn(true)
    featureSwitching.isEnabled(UseInternalAuth) shouldBe true
  }
}