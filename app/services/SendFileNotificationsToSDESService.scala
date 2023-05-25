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

import connectors.SDESConnector
import models.FailedJobResponses.{FailedToProcessNotifications, UnknownProcessingException}
import models.notification.RecordStatusEnum.PERMANENT_FAILURE
import models.{MongoLockResponses, SDESNotificationRecord}
import models.notification.{RecordStatusEnum, SDESNotification}
import play.api.Configuration
import play.api.http.Status.NO_CONTENT
import repositories.FileNotificationRepository
import scheduler.{ScheduleStatus, ScheduledService}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService, MongoLockRepository}
import utils.Logger.logger
import utils.{PagerDutyHelper, TimeMachine}

import java.time.LocalDateTime
import javax.inject.Inject
import utils.PagerDutyHelper.PagerDutyKeys._

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

class SendFileNotificationsToSDESService @Inject()(
                                                    lockRepositoryProvider: MongoLockRepository,
                                                    fileNotificationRepository: FileNotificationRepository,
                                                    sdesConnector: SDESConnector,
                                                    timeMachine: TimeMachine,
                                                    config: Configuration
                                                  )(implicit ec: ExecutionContext) extends ScheduledService[Either[ScheduleStatus.JobFailed, String]] {

  val jobName = "SendFileNotificationsToSDESJob"
  lazy val mongoLockTimeoutSeconds: Int = config.get[Int](s"schedules.$jobName.mongoLockTimeout")

  lazy val lockKeeper: LockService = new LockService() {
    override val lockId = s"schedules.$jobName"
    override val ttl: Duration = mongoLockTimeoutSeconds.seconds
    override val lockRepository: LockRepository = lockRepositoryProvider
  }

  //scalastyle:off
  override def invoke: Future[Either[ScheduleStatus.JobFailed, String]] = {
    tryLock {
      logger.info(s"[$jobName][invoke] - Job started")
      fileNotificationRepository.getPendingNotifications().flatMap {
        notifications => {
          logger.info(s"[SendFileNotificationsToSDESService][invoke] - Amount of notifications: ${notifications.size} before filtering")
          val notificationCandidates = notifications.filter(notification =>
            notification.nextAttemptAt.isEqual(timeMachine.now) || notification.nextAttemptAt.isBefore(timeMachine.now))
          logger.info(s"[SendFileNotificationsToSDESService][invoke] - Amount of notifications: ${notificationCandidates.size} after filtering")
          Future.sequence(notificationCandidates.map {
            notificationWrapper => {
              val notificationToSend: SDESNotification = notificationWrapper.notification
              logger.info(s"[SendFileNotificationsToSDESService][invoke] - Sending notification (reference: ${notificationWrapper.reference}) to SDES")
              sdesConnector.sendNotificationToSDES(notificationToSend).flatMap {
                handleSDESResponse(_, notificationWrapper)
              }.recoverWith {
                case e => {
                  PagerDutyHelper.log("invoke", UNKNOWN_EXCEPTION_FROM_SDES)
                  logger.error(s"[SendFileNotificationsToSDESService][invoke] - Exception occurred processing notifications - message: ${e.getMessage} for reference: ${notificationWrapper.reference}")
                  val updatedNotification: SDESNotificationRecord = setRecordToPermanentFailure(notificationWrapper)
                  fileNotificationRepository.updateFileNotification(updatedNotification).map(_ => false)
                }
              }
            }
          })
        }.map(_.forall(identity))
      }.map {
        if (_) {
          logger.info(s"[SendFileNotificationsToSDESService][invoke] - Processed all notifications in batch")
          Right("Processed all notifications")
        } else {
          PagerDutyHelper.log("invoke", FAILED_TO_PROCESS_FILE_NOTIFICATION)
          logger.error(s"[SendFileNotificationsToSDESService][invoke] - Failed to process all notifications (see previous logs)")
          Left(FailedToProcessNotifications)
        }
      }.recover {
        case e => {
          PagerDutyHelper.log("invoke", UNKNOWN_PROCESSING_EXCEPTION)
          logger.info(s"[SendFileNotificationsToSDESService][invoke] - An unknown exception occurred processing a batch with error: ${e.getMessage}")
          Left(UnknownProcessingException)
        }
      }
    }
  }

  private def handleSDESResponse(response: HttpResponse, notificationWrapper: SDESNotificationRecord): Future[Boolean] = {
    response.status match {
      case NO_CONTENT => {
        logger.info(s"[SendFileNotificationsToSDESService][invoke] - Received NO_CONTENT from connector call to SDES for notification with reference: ${notificationWrapper.reference}")
        val updatedRecord: SDESNotificationRecord = notificationWrapper.copy(status = RecordStatusEnum.SENT, updatedAt = timeMachine.now)
        fileNotificationRepository.updateFileNotification(updatedRecord).map(_ => true)
      }
      case status if status >= 400 => {
        PagerDutyHelper.logStatusCode("invoke", status)(keyOn5xx = Some(RECEIVED_5XX_FROM_SDES), keyOn4xx = Some(RECEIVED_4XX_FROM_SDES))
        logger.warn(s"[SendFileNotificationsToSDESService][invoke] - Received $status status code from connector call to SDES with response body: ${response.body}")
        if (notificationWrapper.numberOfAttempts >= 5) {
          PagerDutyHelper.log("invoke", NOTIFICATION_SET_TO_PERMANENT_FAILURE)
          logger.info(s"[SendFileNotificationsToSDESService][invoke] - Notification (with reference: ${notificationWrapper.reference}) has reached retry threshold of 5 - setting to $PERMANENT_FAILURE")
          val updatedNotification: SDESNotificationRecord = setRecordToPermanentFailure(notificationWrapper)
          fileNotificationRepository.updateFileNotification(updatedNotification).map(_ => false)
        } else {
          logger.info(s"[SendFileNotificationsToSDESService][invoke] - Increasing notification retries and nextAttemptAt for reference: ${notificationWrapper.reference}")
          val updatedNextAttemptAt: LocalDateTime = updateNextAttemptAtTimestamp(notificationWrapper)
          logger.info(s"[SendFileNotificationsToSDESService][invoke] - Setting nextAttemptAt to: $updatedNextAttemptAt for reference: ${notificationWrapper.reference} (retry count: ${notificationWrapper.numberOfAttempts})")
          val updatedNotification: SDESNotificationRecord = notificationWrapper.copy(
            nextAttemptAt = updatedNextAttemptAt,
            numberOfAttempts = notificationWrapper.numberOfAttempts + 1,
            updatedAt = timeMachine.now
          )
          fileNotificationRepository.updateFileNotification(updatedNotification).map(_ => false)
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

  def updateNextAttemptAtTimestamp(record: SDESNotificationRecord): LocalDateTime = {
    record.numberOfAttempts match {
      case 0 => record.nextAttemptAt.plusMinutes(1)
      case 1 => record.nextAttemptAt.plusMinutes(30)
      case 2 => record.nextAttemptAt.plusHours(2)
      case 3 => record.nextAttemptAt.plusHours(4)
      case 4 => record.nextAttemptAt.plusHours(8)
    }
  }

  def setRecordToPermanentFailure(record: SDESNotificationRecord): SDESNotificationRecord = {
    record.copy(status = RecordStatusEnum.PERMANENT_FAILURE, updatedAt = timeMachine.now)
  }
}
