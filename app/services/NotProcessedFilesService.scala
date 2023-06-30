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

import config.AppConfig
import javax.inject.Inject
import models.FailedJobResponses.FailedToProcessNotifications
import models.MongoLockResponses
import models.notification.RecordStatusEnum
import play.api.Configuration
import repositories.FileNotificationRepository
import scheduler.ScheduleStatus.JobFailed
import scheduler.{ScheduleStatus, ScheduledService}
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService, MongoLockRepository}
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys.{FAILED_TO_PROCESS_FILE_NOTIFICATION, MONGO_LOCK_UNKNOWN_EXCEPTION, NOTIFICATION_SET_TO_NOT_PROCESSED_PENDING_RETRY, UNKNOWN_EXCEPTION_FROM_SDES, UNKNOWN_PROCESSING_EXCEPTION}
import utils.{PagerDutyHelper, TimeMachine}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

class NotProcessedFilesService @Inject()(lockRepositoryProvider: MongoLockRepository,
                                         fileNotificationRepository: FileNotificationRepository,
                                         timeMachine: TimeMachine,
                                         config: Configuration,
                                         appConfig: AppConfig
                                        )(implicit ec: ExecutionContext) extends ScheduledService[Either[ScheduleStatus.JobFailed, String]] {

  val jobName = "NotProcessedFilesService"
  lazy val mongoLockTimeoutSeconds: Int = config.get[Int](s"schedules.$jobName.mongoLockTimeout")

  lazy val lockKeeper: LockService = new LockService {
    override val lockId: String = s"schedules.$jobName"
    override val ttl: Duration = mongoLockTimeoutSeconds.seconds
    override val lockRepository: LockRepository = lockRepositoryProvider
  }

  //scalastyle:off
  override def invoke: Future[Either[JobFailed, String]] = {
    tryLock {
      logger.info(s"[$jobName][invoke] - Job started")
      for {
        filesReceivedBySDES <- fileNotificationRepository.getFilesReceivedBySDES()
        filteredFiles = {
          logger.info(s"[NotProcessedFilesService][invoke] - Number of records in ${RecordStatusEnum.FILE_RECEIVED_IN_SDES} state: ${filesReceivedBySDES.size}")
          filesReceivedBySDES.filter(notification => {
            notification.updatedAt.plusMinutes(appConfig.configurableTimeMinutes).isBefore(timeMachine.now)
          })
        }
        sequenceOfResults <- Future.sequence(filteredFiles.map {
          logger.info(s"[NotProcessedFilesService][invoke] - Number of filtered files: ${filteredFiles.size}")
          notification => {
            PagerDutyHelper.log("invoke", NOTIFICATION_SET_TO_NOT_PROCESSED_PENDING_RETRY)
            logger.info(s"[NotProcessedFilesService][invoke] - Updating notification (reference: ${notification.reference}) to NOT_PROCESSED_PENDING_RETRY")
            fileNotificationRepository.updateFileNotification(notification.reference, RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY).map(_ => true)
          }.recover {
            case e => {
              PagerDutyHelper.log("invoke", UNKNOWN_PROCESSING_EXCEPTION)
              logger.error(s"[NotProcessedFilesService][invoke] - Exception occurred processing notification, reference: ${notification.reference} - message: $e")
              false
            }
          }
        })
        isSuccess = sequenceOfResults.forall(identity)
      } yield {
        if(isSuccess) {
          logger.info(s"[NotProcessedFilesService][invoke] - Processed all notifications in batch")
          Right("Processed all notifications")
        } else {
          PagerDutyHelper.log("invoke", FAILED_TO_PROCESS_FILE_NOTIFICATION)
          logger.info(s"[NotProcessedFilesService][invoke] - Failed to process all notifications (see previous logs)")
          Left(FailedToProcessNotifications)
        }
      }
    }
  }

  def tryLock(f: => Future[Either[ScheduleStatus.JobFailed, String]]): Future[Either[ScheduleStatus.JobFailed, String]] = {
    lockKeeper.withLock(f).map {
      case Some(result) => result
      case None =>
        logger.info(s"[$jobName] Locked because it might be running on another instance")
        Right(s"$jobName - JobAlreadyRunning")
    }.recover {
      case e: Exception =>
        PagerDutyHelper.log("tryLock", MONGO_LOCK_UNKNOWN_EXCEPTION)
        logger.info(s"[$jobName] Failed with exception")
        Left(MongoLockResponses.UnknownException(e))
    }
  }
}
