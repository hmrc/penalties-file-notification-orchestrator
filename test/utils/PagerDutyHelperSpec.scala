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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys

class PagerDutyHelperSpec extends AnyWordSpec with Matchers with LogCapturing {
  "log" should {
    s"log a $PagerDutyKeys Value at level WARN with the method name also included" in {
      withCaptureOfLoggingFrom(logger) { capturedEvents =>
        PagerDutyHelper.log("foo", PagerDutyHelper.PagerDutyKeys.RECEIVED_4XX_FROM_SDES)
        capturedEvents.head.getLevel.levelStr shouldBe "WARN"
        capturedEvents.head.getMessage shouldBe s"${PagerDutyHelper.PagerDutyKeys.RECEIVED_4XX_FROM_SDES} - foo"
      }
    }
  }
}
