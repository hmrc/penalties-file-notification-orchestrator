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

import models.SDESNotificationRecord
import models.notification.SDESNotification
import repositories.FileNotificationRepository
import utils.Logger.logger

import javax.inject.Inject
import scala.concurrent.Future

class NotificationMongoService @Inject()(repo: FileNotificationRepository) {

  def insertNotificationRecordsIntoMongo(notifications: Seq[SDESNotification]): Future[Boolean] = {
    logger.info(s"[receiveSDESNotifications][NotificationMongoService][insertNotificationRecordsIntoMongo] - " +
      s"Attempting to insert ${notifications.size} notifications into Mongo")
    val records = notifications.map(notification => SDESNotificationRecord(reference = notification.audit.correlationID, notification = notification))
    repo.insertFileNotifications(records)
  }
}
