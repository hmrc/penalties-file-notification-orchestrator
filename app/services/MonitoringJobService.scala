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

import models.MongoLockResponses
import models.notification.RecordStatusEnum
import play.api.Configuration
import repositories.FileNotificationRepository
import scheduler.{ScheduleStatus, ScheduledService}
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService, MongoLockRepository}
import utils.Logger.logger
import utils.PagerDutyHelper
import utils.PagerDutyHelper.PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION

import javax.inject.Inject
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

class MonitoringJobService @Inject()(
                                      lockRepositoryProvider: MongoLockRepository,
                                      config: Configuration,
                                      repository: FileNotificationRepository
                                    )(implicit ec: ExecutionContext) extends ScheduledService[Either[ScheduleStatus.JobFailed, Seq[String]]] {

  val jobName = "MonitoringJob"
  lazy val mongoLockTimeoutSeconds: Int = config.get[Int](s"schedules.$jobName.mongoLockTimeout")

  lazy val lockKeeper: LockService = new LockService() {
    override val lockId = s"schedules.$jobName"
    override val ttl: Duration = mongoLockTimeoutSeconds.seconds
    override val lockRepository: LockRepository = lockRepositoryProvider
  }

  override def invoke: Future[Either[ScheduleStatus.JobFailed, Seq[String]]] = {
    tryLock {
      logger.debug(s"[$jobName][invoke] - Job started")
      for {
        countOfPendingNotifications <- repository.countRecordsByStatus(RecordStatusEnum.PENDING)
        countOfSentNotifications <- repository.countRecordsByStatus(RecordStatusEnum.SENT)
        countOfFilesReceivedNotifications <- repository.countRecordsByStatus(RecordStatusEnum.FILE_RECEIVED_IN_SDES)
        countOfFilesNotReceivedNotifications <- repository.countRecordsByStatus(RecordStatusEnum.FILE_NOT_RECEIVED_IN_SDES_PENDING_RETRY)
        countOfFilesProcessedNotifications <- repository.countRecordsByStatus(RecordStatusEnum.FILE_PROCESSED_IN_SDES)
        countOfFailedPendingRetry <- repository.countRecordsByStatus(RecordStatusEnum.FAILED_PENDING_RETRY)
        countOfNotProcessedPendingRetry <- repository.countRecordsByStatus(RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY)
        countOfFailureNotifications <- repository.countRecordsByStatus(RecordStatusEnum.PERMANENT_FAILURE)
      } yield {
        val logOfPendingNotificationsCount = s"[MonitoringJobService][invoke] - Count of Pending Notifications: $countOfPendingNotifications"
        val logOfSentNotificationsCount = s"[MonitoringJobService][invoke] - Count of Sent Notifications: $countOfSentNotifications"
        val logOfFileReceivedNotificationsCount = s"[MonitoringJobService][invoke] - Count of File Received in SDES Notifications: $countOfFilesReceivedNotifications"
        val logOfFileNotReceivedNotificationsCount = s"[MonitoringJobService][invoke] - Count of File Not Received in SDES Notifications: $countOfFilesNotReceivedNotifications"
        val logOfFileProcessedNotificationsCount = s"[MonitoringJobService][invoke] - Count of File Processed in SDES Notifications: $countOfFilesProcessedNotifications"
        val logOfFailedPendingRetryNotificationsCount = s"[MonitoringJobService][invoke] - Count of Failed Pending Retry Notifications: $countOfFailedPendingRetry"
        val logOfNotProcessedPendingRetryNotificationsCount = s"[MonitoringJobService][invoke] - Count of Not Processed Pending Retry Notifications: $countOfNotProcessedPendingRetry"
        val logOfFailedNotificationsCount = s"[MonitoringJobService][invoke] - Count of Failed Notifications: $countOfFailureNotifications"
        val seqOfLogs = Seq(logOfPendingNotificationsCount, logOfSentNotificationsCount, logOfFileReceivedNotificationsCount, logOfFileNotReceivedNotificationsCount, logOfFileProcessedNotificationsCount, logOfFailedPendingRetryNotificationsCount, logOfNotProcessedPendingRetryNotificationsCount, logOfFailedNotificationsCount)
        seqOfLogs.foreach(logger.info(_))
        Right(seqOfLogs)
      }
    }
  }

  def tryLock(f: => Future[Either[ScheduleStatus.JobFailed, Seq[String]]]): Future[Either[ScheduleStatus.JobFailed, Seq[String]]] = {
    lockKeeper.withLock(f).map {
      case Some(result) => result
      case None =>
        logger.info(s"[$jobName] Locked because it might be running on another instance")
        Right(Seq(s"$jobName - JobAlreadyRunning"))
    }.recover {
      case e: Exception =>
        PagerDutyHelper.log("tryLock", MONGO_LOCK_UNKNOWN_EXCEPTION)
        logger.info(s"[$jobName] Failed with exception")
        Left(MongoLockResponses.UnknownException(e))
    }
  }
}
