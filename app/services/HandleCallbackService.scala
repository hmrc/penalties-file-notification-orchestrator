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

import models.notification.RecordStatusEnum
import repositories.FileNotificationRepository
import utils.Logger.logger
import utils.PagerDutyHelper
import utils.PagerDutyHelper.PagerDutyKeys.FAILED_TO_PROCESS_FILE_NOTIFICATION

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HandleCallbackService @Inject()(fileNotificationRepository: FileNotificationRepository)
                                     (implicit ec: ExecutionContext) {

  def updateNotificationAfterCallback(reference: String, updatedStatus: RecordStatusEnum.Value): Future[Either[String, Unit]] = {
    fileNotificationRepository.updateFileNotification(reference, updatedStatus).map {
      _ => Right((): Unit)
    }.recover {
      case e: Exception => {
        logger.error(s"[updateNotificationAfterCallback][updateNotificationAfterCallback] Failed to update record (with reference: $reference with error: ${e.getMessage}")
        PagerDutyHelper.log("updateNotificationAfterCallback", FAILED_TO_PROCESS_FILE_NOTIFICATION)
        Left("Failed to update notification")
      }
    }
  }
}
