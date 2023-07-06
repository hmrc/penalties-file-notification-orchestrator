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

package services

import base.SpecBase
import models.notification._
import org.mockito.ArgumentMatchers
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.FileNotificationRepository

import scala.concurrent.Future

class NotificationMongoServiceSpec extends SpecBase {
  val mockRepo: FileNotificationRepository = mock[FileNotificationRepository]

  class Setup {
    val service = new NotificationMongoService(mockRepo)
    reset(mockRepo)
  }

  val fileNotifications: Seq[SDESNotification] = Seq(
    SDESNotification(
      informationType = "type",
      file = SDESNotificationFile(
        recipientOrSender = "recipient",
        name = "file1.txt",
        location = "http://example.com",
        checksum = SDESChecksum(
          algorithm = "SHA-256",
          value = "123456789-abcdef-123456789"
        ),
        size = 1,
        properties = Seq(
          SDESProperties(
            name = "name",
            value = "value"
          ))
      ),
      audit = SDESAudit(
        correlationID = "12345"
      )
    )
  )

  "insertNotificationRecordsIntoMongo" should {
    "call the repository and return true when successfully inserted" in new Setup {
      when(mockRepo.insertFileNotifications(ArgumentMatchers.any())).thenReturn(Future.successful(true))
      val result: Boolean = await(service.insertNotificationRecordsIntoMongo(fileNotifications))
      result shouldBe true
    }

    "call the repository and return false when unsuccessful" in new Setup {
      when(mockRepo.insertFileNotifications(ArgumentMatchers.any())).thenReturn(Future.successful(false))
      val result: Boolean = await(service.insertNotificationRecordsIntoMongo(fileNotifications))
      result shouldBe false
    }
  }
}
