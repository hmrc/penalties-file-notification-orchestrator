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

package models.notification

import base.SpecBase
import play.api.libs.json.{JsString, Json}

class RecordStatusEnumSpec extends SpecBase {

  "RecordStatusEnum" should {

    "be writable to Json" when {

      "status is PENDING" in {
        val result = Json.toJson(RecordStatusEnum.PENDING)
        result shouldBe JsString("PENDING")
      }

      "status is SENT" in {
        val result = Json.toJson(RecordStatusEnum.SENT)
        result shouldBe JsString("SENT")
      }

      "status is PERMANENT_FAILURE" in {
        val result = Json.toJson(RecordStatusEnum.PERMANENT_FAILURE)
        result shouldBe JsString("PERMANENT_FAILURE")
      }
    }

    "be readable from Json" when {

      "status is PENDING" in {
        val result = Json.fromJson(JsString("PENDING"))(RecordStatusEnum.format)
        result.isSuccess shouldBe true
        result.get shouldBe RecordStatusEnum.PENDING
      }

      "status is SENT" in {
        val result = Json.fromJson(JsString("SENT"))(RecordStatusEnum.format)
        result.isSuccess shouldBe true
        result.get shouldBe RecordStatusEnum.SENT
      }

      "status is PERMANENT_FAILURE" in {
        val result = Json.fromJson(JsString("PERMANENT_FAILURE"))(RecordStatusEnum.format)
        result.isSuccess shouldBe true
        result.get shouldBe RecordStatusEnum.PERMANENT_FAILURE
      }
    }
  }
}
