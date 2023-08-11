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
import models.SDESNotificationRecord
import models.notification._
import org.mockito.ArgumentMatchers.any
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import utils.LogCapturing
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}

class HandleCallbackServiceSpec extends SpecBase with LogCapturing {
  val mockRepository: FileNotificationRepository = mock[FileNotificationRepository]
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  val service = new HandleCallbackService(mockRepository)

  class Setup {
    reset(mockRepository)
  }

  "updateNotificationAfterCallback" should {
    "return Left" when {
      "the repository throws an exception - logging a PagerDuty" in new Setup {
        when(mockRepository.updateFileNotification(any(), any())).thenReturn(Future.failed(new Exception("60% of the time, it works everytime")))
        withCaptureOfLoggingFrom(logger) {
          logs => {
            val result: Either[String, Unit] = await(service.updateNotificationAfterCallback("ref1", RecordStatusEnum.FILE_PROCESSED_IN_SDES))
            result.isLeft shouldBe true
            result.left.toOption.get shouldBe "Failed to update notification"
            logs.exists(_.getMessage.contains(FAILED_TO_PROCESS_FILE_NOTIFICATION.toString)) shouldBe true
          }
        }
      }
    }

    "return Right" when {
      "the repository doesn't throw an exception" in new Setup {
        val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
          reference = "ref",
          status = RecordStatusEnum.SENT,
          numberOfAttempts = 1,
          createdAt = LocalDateTime.of(2020, 1, 1, 1, 1).toInstant(ZoneOffset.UTC),
          updatedAt = LocalDateTime.of(2020, 2, 2, 2, 2).toInstant(ZoneOffset.UTC),
          nextAttemptAt = LocalDateTime.of(2020, 3, 3, 3, 3).toInstant(ZoneOffset.UTC),
          notification = SDESNotification(
            informationType = "info",
            file = SDESNotificationFile(
              recipientOrSender = "penalties",
              name = "file1.txt",
              location = "http://example.com",
              checksum = SDESChecksum(
                algorithm = "SHA-256",
                value = "abcdef-123456789-abcdef"
              ),
              size = 256,
              properties = Seq.empty[SDESProperties]
            ),
            audit = SDESAudit("123456789-abcdefgh-987654321")
          )
        )
        when(mockRepository.updateFileNotification(any(), any())).thenReturn(Future.successful(notificationRecord))
        val result: Either[String, Unit] = await(service.updateNotificationAfterCallback("ref1", RecordStatusEnum.FILE_PROCESSED_IN_SDES))
        result.isRight shouldBe true
      }
    }
  }

}
