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

package repositories

import com.mongodb.client.model.Updates.{combine, set}
import config.AppConfig
import models.SDESNotificationRecord
import models.notification.RecordStatusEnum
import org.mongodb.scala.model.Filters.{equal, in}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import utils.Logger.logger
import utils.PagerDutyHelper
import utils.PagerDutyHelper.PagerDutyKeys._

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileNotificationRepository @Inject()(mongoComponent: MongoComponent,
                                           appConfig: AppConfig)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[SDESNotificationRecord](
    collectionName = "sdes-file-notifications",
    mongoComponent = mongoComponent,
    domainFormat = SDESNotificationRecord.mongoFormats,
    indexes = Seq(
      IndexModel(ascending("reference"), IndexOptions().unique(true)),
      IndexModel(ascending("status")),
      IndexModel(ascending("createdAt"), IndexOptions().expireAfter(appConfig.notificationTtl, TimeUnit.HOURS))
    )) with MongoJavatimeFormats {

  implicit val dateFormat: Format[LocalDateTime] = localDateTimeFormat
  implicit val mongoFormats: OFormat[SDESNotificationRecord] = Json.format[SDESNotificationRecord]

  def insertFileNotifications(records: Seq[SDESNotificationRecord]): Future[Boolean] = {
    collection.insertMany(records).toFuture().map(_.wasAcknowledged())
      .recover {
        case e =>
          PagerDutyHelper.log("insertFileNotifications", FAILED_TO_INSERT_SDES_NOTIFICATION)
          logger.error(s"[FileNotificationRepository][insertFileNotifications] - Failed to insert SDES notification with message: ${e.getMessage}")
          false
      }
  }

  def updateFileNotification(record: SDESNotificationRecord): Future[SDESNotificationRecord] = {
    logger.info(s"[FileNotificationRepository][updateFileNotification] - Updating record ${record.reference} in Mongo")
    collection.findOneAndUpdate(equal("reference", record.reference), combine(
      set("nextAttemptAt", Codecs.toBson(record.nextAttemptAt)),
      set("status", record.status.toString),
      set("numberOfAttempts", record.numberOfAttempts),
      set("updatedAt", Codecs.toBson(record.updatedAt))
    )).toFuture()
  }

  def getPendingNotifications(): Future[Seq[SDESNotificationRecord]] = {
    collection.find(in("status", Seq(
      RecordStatusEnum.PENDING.toString,
      RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY.toString,
      RecordStatusEnum.FAILED_PENDING_RETRY.toString): _*
    )).toFuture()
  }

  def countRecordsByStatus(status: RecordStatusEnum.Value): Future[Long] = {
    collection.countDocuments(equal("status", status.toString)).toFuture()
  }
}
