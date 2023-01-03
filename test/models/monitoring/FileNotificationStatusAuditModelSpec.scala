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

package models.monitoring

import base.SpecBase
import models.SDESFileNotificationEnum

import java.time.LocalDateTime

class FileNotificationStatusAuditModelSpec extends SpecBase {
  "detail" should {
    "have all the fields including failureReason when its defined" in {
      val fileNotificationStatusAuditModel: FileNotificationStatusAuditModel = FileNotificationStatusAuditModel(
        fileStatus = SDESFileNotificationEnum.FileProcessingFailure,
        fileName = "xyz.doc",
        correlationId = "12345-abcdef-6789",
        failureReason = Some("A virus was detected"),
        availableUntil = None
      )
      (fileNotificationStatusAuditModel.detail \ "fileStatus").validate[String].get shouldBe "FileProcessingFailure"
      (fileNotificationStatusAuditModel.detail \ "fileName").validate[String].get shouldBe "xyz.doc"
      (fileNotificationStatusAuditModel.detail \ "correlationId").validate[String].get shouldBe "12345-abcdef-6789"
      (fileNotificationStatusAuditModel.detail \ "failureReason").validate[String].get shouldBe "A virus was detected"
      (fileNotificationStatusAuditModel.detail \ "availableUntil").isEmpty shouldBe true
    }

    "have all the fields including availableUntil when its defined" in {
      val fileNotificationStatusAuditModel: FileNotificationStatusAuditModel = FileNotificationStatusAuditModel(
        fileStatus = SDESFileNotificationEnum.FileReady,
        fileName = "xyz.doc",
        correlationId = "12345-abcdef-6789",
        failureReason = None,
        availableUntil = Some(LocalDateTime.of(2022, 1, 1, 1, 1, 0))
      )
      (fileNotificationStatusAuditModel.detail \ "fileStatus").validate[String].get shouldBe "FileReady"
      (fileNotificationStatusAuditModel.detail \ "fileName").validate[String].get shouldBe "xyz.doc"
      (fileNotificationStatusAuditModel.detail \ "correlationId").validate[String].get shouldBe "12345-abcdef-6789"
      (fileNotificationStatusAuditModel.detail \ "failureReason").isEmpty shouldBe true
      (fileNotificationStatusAuditModel.detail \ "availableUntil").validate[String].get shouldBe "2022-01-01T01:01:00"
    }
  }
}
