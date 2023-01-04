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

package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsString, Json}

class SDESFileNotificationEnumSpec extends AnyWordSpec with Matchers {

  "be readable from JSON for 'FileReady'" in {
    val result = Json.fromJson(JsString("FileReady"))(SDESFileNotificationEnum.format)
    result.isSuccess shouldBe true
    result.get shouldBe SDESFileNotificationEnum.FileReady
  }

  "be readable from JSON for 'FileReceived'" in {
    val result = Json.fromJson(JsString("FileReceived"))(SDESFileNotificationEnum.format)
    result.isSuccess shouldBe true
    result.get shouldBe SDESFileNotificationEnum.FileReceived
  }

  "be readable from JSON for 'FileProcessingFailure'" in {
    val result = Json.fromJson(JsString("FileProcessingFailure"))(SDESFileNotificationEnum.format)
    result.isSuccess shouldBe true
    result.get shouldBe SDESFileNotificationEnum.FileProcessingFailure
  }

  "be readable from JSON for 'FileProcessed'" in {
    val result = Json.fromJson(JsString("FileProcessed"))(SDESFileNotificationEnum.format)
    result.isSuccess shouldBe true
    result.get shouldBe SDESFileNotificationEnum.FileProcessed
  }

  "throw an error" when {
    "the value can't be read" in {
      val result = Json.fromJson(JsString("xyz"))(SDESFileNotificationEnum.format)
      result.isError shouldBe true
    }
  }
}
