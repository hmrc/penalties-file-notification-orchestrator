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

import connectors.SDESConnector
import models.FailedJobResponses.{FailedToProcessNotifications, UnknownProcessingException}
import models.SDESNotificationRecord
import models.notification.{RecordStatusEnum, SDESNotification}
import org.joda.time.Duration
import play.api.Configuration
import play.api.http.Status.OK
import repositories.{FileNotificationRepository, LockRepositoryProvider, MongoLockResponses}
import scheduler.{ScheduleStatus, ScheduledService}
import uk.gov.hmrc.lock.LockKeeper
import utils.Logger.logger
import utils.TimeMachine

import java.time.LocalDateTime
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SendFileNotificationsToSDESService @Inject()(
                                                    lockRepositoryProvider: LockRepositoryProvider,
                                                    fileNotificationRepository: FileNotificationRepository,
                                                    sdesConnector: SDESConnector,
                                                    timeMachine: TimeMachine,
                                                    config: Configuration
                                                  )(implicit ec: ExecutionContext) extends ScheduledService[Either[ScheduleStatus.JobFailed, String]] {

  val jobName = "SendFileNotificationsToSDESJob"
  lazy val mongoLockTimeoutSeconds: Int = config.get[Int](s"schedules.$jobName.mongoLockTimeout")

  lazy val lockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = s"schedules.$jobName"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(mongoLockTimeoutSeconds)
    override lazy val repo = lockRepositoryProvider.repo
  }

  //scalastyle:off
  override def invoke: Future[Either[ScheduleStatus.JobFailed, String]] = {
    tryLock {
      logger.info(s"[$jobName][invoke] - Job started")
      fileNotificationRepository.getPendingNotifications().flatMap {
        notifications => {
          logger.debug(s"[SendFileNotificationsToSDESService][invoke] - Amount of notifications: ${notifications.size} before filtering")
          val notificationCandidates = notifications.filter(notification =>
            notification.nextAttemptAt.isEqual(timeMachine.now) || notification.nextAttemptAt.isBefore(timeMachine.now))
          logger.debug(s"[SendFileNotificationsToSDESService][invoke] - Amount of notifications: ${notificationCandidates.size} after filtering")
          Future.sequence(notificationCandidates.map {
            notificationWrapper => {
              val notificationToSend: SDESNotification = notificationWrapper.notification
              sdesConnector.sendNotificationToSDES(notificationToSend).flatMap {
                _.status match {
                  case OK => {
                    logger.debug(s"[SendFileNotificationsToSDESService][invoke] - Received OK from connector call to SDES")
                    val updatedRecord: SDESNotificationRecord = notificationWrapper.copy(status = RecordStatusEnum.SENT, updatedAt = timeMachine.now)
                    fileNotificationRepository.updateFileNotification(updatedRecord).map(_ => true)
                  }
                  case status if status >= 500 => {
                    logger.warn(s"[SendFileNotificationsToSDESService][invoke] - Received 5xx status ($status) from connector call to SDES")
                    if(notificationWrapper.numberOfAttempts >= 5) {
                      logger.debug(s"[SendFileNotificationsToSDESService][invoke] - Notification has reached retry threshold of 5 - setting to PERMANENT_FAILURE")
                      val updatedNotification: SDESNotificationRecord = setRecordToPermanentFailure(notificationWrapper)
                      fileNotificationRepository.updateFileNotification(updatedNotification).map(_ => false)
                    } else {
                      logger.debug(s"[SendFileNotificationsToSDESService][invoke] - Increasing notification retries and nextAttemptAt")
                      val updatedNextAttemptAt: LocalDateTime = updateNextAttemptAtTimestamp(notificationWrapper)
                      logger.debug(s"[SendFileNotificationsToSDESService][invoke] - Setting nextAttemptAt to: $updatedNextAttemptAt (retry count: ${notificationWrapper.numberOfAttempts})")
                      val updatedNotification: SDESNotificationRecord = notificationWrapper.copy(
                        nextAttemptAt = updatedNextAttemptAt,
                        numberOfAttempts = notificationWrapper.numberOfAttempts + 1,
                        updatedAt = timeMachine.now
                      )
                      fileNotificationRepository.updateFileNotification(updatedNotification).map(_ => false)
                    }
                  }
                  case status if status >= 400 => {
                    logger.error(s"[SendFileNotificationsToSDESService][invoke] - Received 4xx status ($status) from connector call to SDES")
                    val updatedNotification: SDESNotificationRecord = setRecordToPermanentFailure(notificationWrapper)
                    fileNotificationRepository.updateFileNotification(updatedNotification).map(_ => false)
                  }
                }
              }.recoverWith {
                case e => {
                  logger.error(s"[SendFileNotificationsToSDESService][invoke] - Exception occurred processing notifications - message: ${e.getMessage}")
                  val updatedNotification: SDESNotificationRecord = setRecordToPermanentFailure(notificationWrapper)
                  fileNotificationRepository.updateFileNotification(updatedNotification).map(_ => false)
                }
              }
            }
          })
        }.map(_.forall(identity))
      }.map {
        if (_) Right("Processed all notifications") else Left(FailedToProcessNotifications)
      }.recover {
        case _ => Left(UnknownProcessingException)
      }
    }
  }

  def tryLock(f: => Future[Either[ScheduleStatus.JobFailed, String]]): Future[Either[ScheduleStatus.JobFailed, String]] = {
    lockKeeper.tryLock(f).map {
      case Some(result) => result
      case None =>
        logger.info(s"[$jobName] Locked because it might be running on another instance")
        Right(s"$jobName - JobAlreadyRunning")
    }.recover {
      case e: Exception =>
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
