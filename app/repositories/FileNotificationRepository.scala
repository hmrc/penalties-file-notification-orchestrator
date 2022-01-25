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

package repositories

import config.AppConfig
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import models.SDESNotificationRecord
import models.notification.RecordStatusEnum
import org.mongodb.scala.model.Filters.equal

import javax.inject.Inject
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.Logger.logger

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

class FileNotificationRepository @Inject()(mongoComponent: MongoComponent,
                                           appConfig: AppConfig)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[SDESNotificationRecord](
    collectionName = "sdes-file-notifications",
    mongoComponent = mongoComponent,
    domainFormat = SDESNotificationRecord.mongoFormats,
    indexes = Seq(
      IndexModel(
        ascending("reference"), IndexOptions().unique(true)
      ),
      IndexModel(ascending("status")),
      IndexModel(ascending("createdAt"), IndexOptions().expireAfter(appConfig.notificationTtl, TimeUnit.HOURS))
    )) {

  def insertFileNotifications(records: Seq[SDESNotificationRecord]): Future[Boolean] = {
    collection.insertMany(records).toFuture().map(_.wasAcknowledged())
      .recover {
        case e =>
          logger.error(s"[FileNotificationRepository][storeFileNotifications] - Failed to insert SDES notification with message: ${e.getMessage}")
          false
      }
  }

  def getPendingNotifications(): Future[Seq[SDESNotificationRecord]] = {
    collection.find(equal("status", RecordStatusEnum.PENDING.toString)).toFuture()
  }

}
