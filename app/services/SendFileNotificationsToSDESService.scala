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

import org.joda.time.Duration
import play.api.Configuration
import repositories.{LockRepositoryProvider, MongoLockResponses}
import scheduler.{ScheduleStatus, ScheduledService}
import uk.gov.hmrc.lock.LockKeeper
import utils.Logger.logger

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SendFileNotificationsToSDESService @Inject()(
                                                    lockRepositoryProvider: LockRepositoryProvider,
                                                    config: Configuration
                                                  )(implicit ec: ExecutionContext) extends ScheduledService[Either[ScheduleStatus.JobFailed, Seq[String]]] {

  val jobName = "SendFileNotificationsToSDESJob"
  lazy val mongoLockTimeoutSeconds: Int = config.get[Int](s"schedules.$jobName.mongoLockTimeout")

  lazy val lockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = s"schedules.$jobName"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(mongoLockTimeoutSeconds)
    override lazy val repo = lockRepositoryProvider.repo
  }

  override def invoke: Future[Either[ScheduleStatus.JobFailed, Seq[String]]] = {
    tryLock {
      logger.debug(s"[$jobName][invoke] - Job started")
      Future.successful(Right(Seq.empty))
    }
  }

  def tryLock(f: => Future[Either[ScheduleStatus.JobFailed, Seq[String]]]): Future[Either[ScheduleStatus.JobFailed, Seq[String]]] = {
    lockKeeper.tryLock(f).map {
      case Some(result) => result
      case None =>
        logger.info(s"[$jobName] Locked because it might be running on another instance")
        Right(Seq(s"$jobName - JobAlreadyRunning"))
    }.recover {
      case e: Exception =>
        logger.info(s"[$jobName] Failed with exception")
        Left(MongoLockResponses.UnknownException(e))
    }
  }
}
