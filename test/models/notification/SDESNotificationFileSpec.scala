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

package models.notification

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

class SDESNotificationFileSpec extends AnyWordSpec with Matchers {

  val model: SDESNotificationFile = SDESNotificationFile(
    recipientOrSender = "recipient1",
    name = "file1.txt",
    location = "http://example.com/file1.txt",
    checksum = SDESChecksum(algorithm = "SHA-256", value = "check123"),
    size = 1,
    properties = Seq(
      SDESProperties(name = "name", value = "value")
    )
  )

  val modelAsJson: JsValue = Json.parse(
    """
      |{
      | "recipientOrSender": "recipient1",
      | "name": "file1.txt",
      | "location": "http://example.com/file1.txt",
      | "checksum": {
      |   "algorithm": "SHA-256",
      |   "value": "check123"
      | },
      | "size": 1,
      | "properties": [
      |   {
      |     "name": "name",
      |     "value": "value"
      |   }
      | ]
      |}
      |""".stripMargin)

  "be readable from JSON" in {
    val result = Json.fromJson(modelAsJson)(SDESNotificationFile.mongoFormats)
    result.isSuccess shouldBe true
    result.get shouldBe model
  }

  "be writable to JSON" in {
    val result = Json.toJson(model)(SDESNotificationFile.mongoFormats)
    result shouldBe modelAsJson
  }
}
