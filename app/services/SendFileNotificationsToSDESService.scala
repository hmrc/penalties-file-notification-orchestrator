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
import models.FailedJobResponses.FailedToProcessNotifications
import models.notification.{RecordStatusEnum, SDESNotification}
import org.joda.time.Duration
import play.api.Configuration
import play.api.http.Status.OK
import repositories.{FileNotificationRepository, LockRepositoryProvider, MongoLockResponses}
import scheduler.{ScheduleStatus, ScheduledService}
import uk.gov.hmrc.lock.LockKeeper
import utils.Logger.logger
import utils.TimeMachine

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
                    val updatedRecord = notificationWrapper.copy(status = RecordStatusEnum.SENT, updatedAt = timeMachine.now)
                    fileNotificationRepository.updateFileNotification(updatedRecord).map(_ => true)
                  }
                  case status if status >= 500 => {
                    logger.warn(s"[SendFileNotificationsToSDESService][invoke] - Received 5xx status ($status) from connector call to SDES")
                    //increment retries and set nextAttemptAt to configured values - if retries >= threshold then PERMANENT_FAILURE
                    Future.successful(false)
                  }
                  case status if status >= 400 => {
                    logger.error(s"[SendFileNotificationsToSDESService][invoke] - Received 4xx status ($status) from connector call to SDES")
                    //set to PERMANENT_FAILURE
                    Future.successful(false)
                  }
                }
              }.recover {
                case e => {
                  logger.error(s"[SendFileNotificationsToSDESService][invoke] - Exception occurred processing notifications - message: ${e.getMessage}")
                  //set to PERMANENT_FAILURE
                  false
                }
              }
            }
          })
        }.map(_.forall(identity))
      }.map {
        if (_) Right("Processed all notifications") else Left(FailedToProcessNotifications)
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
}
