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

package services

import base.SpecBase
import models.notification.{SDESAudit, SDESChecksum, SDESNotification, SDESNotificationFile, SDESProperties}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{mock, reset, when}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.FileNotificationRepository

import scala.concurrent.Future

class NotificationMongoServiceSpec extends SpecBase {
  val mockRepo: FileNotificationRepository = mock(classOf[FileNotificationRepository])

  class Setup {
    val service = new NotificationMongoService(
      mockRepo
    )

    reset(mockRepo)
  }

  val seqToPassToService = Seq(
    SDESNotification(
      informationType = "type",
      file = SDESNotificationFile(
        recipientOrSender = "recipient",
        name = "John Doe",
        location = "place",
        checksum = SDESChecksum(
          algorithm = "beep",
          value = "abc"
        ),
        size = 1,
        properties = Seq(
          SDESProperties(
            name = "name",
            value = "xyz"
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
      val result: Boolean = await(service.insertNotificationRecordsIntoMongo(seqToPassToService))
      result shouldBe true
    }

    "call the repository and return false when unsuccessful" in new Setup {
      when(mockRepo.insertFileNotifications(ArgumentMatchers.any())).thenReturn(Future.successful(false))
      val result: Boolean = await(service.insertNotificationRecordsIntoMongo(seqToPassToService))
      result shouldBe false
    }
  }
}
