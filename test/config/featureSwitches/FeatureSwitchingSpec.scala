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
import org.scalatest.BeforeAndAfterAll
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class FeatureSwitchingSpec extends SpecBase with BeforeAndAfterAll with FeatureSwitching {
  val mockConfig: Configuration = mock[Configuration]
  val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  val config: AppConfig = new AppConfig(mockConfig, mockServicesConfig)

  class Setup {
    reset(mockConfig, mockServicesConfig)
    val featureSwitching: FeatureSwitching = new FeatureSwitching {
      override implicit val appConfig: AppConfig = config
    }
    FeatureSwitch.listOfAllFeatureSwitches.foreach(
      featureSwitch => sys.props -= featureSwitch.name
    )
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    FeatureSwitch.listOfAllFeatureSwitches.foreach(
      featureSwitch => sys.props -= featureSwitch.name
    )
  }

  "constants" should {
    "be true and false" in new Setup {
      featureSwitching.FEATURE_SWITCH_ON shouldBe "true"
      featureSwitching.FEATURE_SWITCH_OFF shouldBe "false"
    }
  }

// No feature switches setup, worth keeping in case more are added in the future
//  "isEnabled" should {
//    s"return true if feature switch is enabled" in new Setup {
//      featureSwitching.enableFeatureSwitch(UseInternalAuth)
//      featureSwitching.isEnabled(UseInternalAuth) shouldBe true
//    }
//
//    s"return false if feature switch is disabled" in new Setup {
//      featureSwitching.disableFeatureSwitch(UseInternalAuth)
//      featureSwitching.isEnabled(UseInternalAuth) shouldBe false
//    }
//
//    "return true if system props is empty but config has value" in new Setup {
//      when(mockConfig.get[Boolean](any())(any()))
//        .thenReturn(true)
//      featureSwitching.isEnabled(UseInternalAuth) shouldBe true
//    }
//  }

  "enableFeatureSwitch" should {
    FeatureSwitch.listOfAllFeatureSwitches.foreach(
      featureSwitch => {
        s"set ${featureSwitch.name} property to true" in new Setup {
          featureSwitching.enableFeatureSwitch(featureSwitch)
          sys.props.get(featureSwitch.name).get shouldBe "true"
        }
      }
    )
  }

  "disableFeatureSwitch" should {
    FeatureSwitch.listOfAllFeatureSwitches.foreach(
      featureSwitch => {
        s"set ${featureSwitch.name} property to false" in new Setup {
          featureSwitching.disableFeatureSwitch(featureSwitch)
          sys.props.get(featureSwitch.name).get shouldBe "false"
        }
      }
    )
  }
}
