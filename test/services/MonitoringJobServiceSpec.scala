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

import base.SpecBase
import models.MongoLockResponses
import models.notification.RecordStatusEnum
import org.mockito.ArgumentMatchers
import org.scalatest.concurrent.Eventually.eventually
import play.api.Configuration
import play.api.test.Helpers._
import repositories.FileNotificationRepository
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.LogCapturing
import utils.Logger.logger
import utils.PagerDutyHelper.PagerDutyKeys

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class MonitoringJobServiceSpec extends SpecBase with LogCapturing {
  val mockLockRepository: MongoLockRepository = mock[MongoLockRepository]
  val mockConfig: Configuration = mock[Configuration]
  val mockRepo: FileNotificationRepository = mock[FileNotificationRepository]
  val jobName = "MonitoringJob"

  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds

  class Setup(withMongoLockStubs: Boolean = true) {
    reset(mockLockRepository, mockLockRepository, mockConfig, mockRepo)
    val service = new MonitoringJobService(mockLockRepository, mockConfig, mockRepo)

    when(mockConfig.get[Int](ArgumentMatchers.eq(s"schedules.${service.jobName}.mongoLockTimeout"))(ArgumentMatchers.any()))
      .thenReturn(mongoLockTimeout)

    if (withMongoLockStubs) {
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))
    }
  }

  "invoke" should {
    "return the count of all records by Status and log them out" in new Setup {
      when(mockRepo.countRecordsByStatus(ArgumentMatchers.eq(RecordStatusEnum.PENDING))).thenReturn(Future.successful(1L))
      when(mockRepo.countRecordsByStatus(ArgumentMatchers.eq(RecordStatusEnum.SENT))).thenReturn(Future.successful(2L))
      when(mockRepo.countRecordsByStatus(ArgumentMatchers.eq(RecordStatusEnum.PERMANENT_FAILURE))).thenReturn(Future.successful(3L))
      when(mockRepo.countRecordsByStatus(ArgumentMatchers.eq(RecordStatusEnum.FILE_RECEIVED_IN_SDES))).thenReturn(Future.successful(4L))
      when(mockRepo.countRecordsByStatus(ArgumentMatchers.eq(RecordStatusEnum.FILE_PROCESSED_IN_SDES))).thenReturn(Future.successful(5L))
      when(mockRepo.countRecordsByStatus(ArgumentMatchers.eq(RecordStatusEnum.FAILED_PENDING_RETRY))).thenReturn(Future.successful(6L))
      when(mockRepo.countRecordsByStatus(ArgumentMatchers.eq(RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY))).thenReturn(Future.successful(7L))


      withCaptureOfLoggingFrom(logger){
        logs => {
          val result = await(service.invoke)
          result.isRight shouldBe true
          result.toOption.get shouldBe Seq(
            "[MonitoringJobService][invoke] - Count of Pending Notifications: 1",
            "[MonitoringJobService][invoke] - Count of Sent Notifications: 2",
            "[MonitoringJobService][invoke] - Count of Failed Notifications: 3",
            "[MonitoringJobService][invoke] - Count of File Received in SDES Notifications: 4",
            "[MonitoringJobService][invoke] - Count of File Processed in SDES Notifications: 5",
            "[MonitoringJobService][invoke] - Count of Failed Pending Retry Notifications: 6",
            "[MonitoringJobService][invoke] - Count of Not Processed Pending Retry Notifications: 7"
          )
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Pending Notifications: 1") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Sent Notifications: 2") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Failed Notifications: 3") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of File Received in SDES Notifications: 4") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of File Processed in SDES Notifications: 5") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Failed Pending Retry Notifications: 6") shouldBe true
          logs.exists(_.getMessage == "[MonitoringJobService][invoke] - Count of Not Processed Pending Retry Notifications: 7") shouldBe true
        }
      }
    }
  }

  "tryLock" should {
    "return a Future successful when lockRepository is able to lock and unlock successfully" in new Setup {
      val expectingResult: Future[Right[Nothing, Seq[Nothing]]] = Future.successful(Right(Seq.empty))
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))
      await(service.tryLock(expectingResult)) shouldBe Right(Seq.empty)
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return a $Right ${Seq.empty} if lock returns Future.successful (false)" in new Setup {
      val expectingResult = Future.successful(Right(Seq.empty))
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.successful(false))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(expectingResult)) shouldBe Right(Seq(s"$jobName - JobAlreadyRunning"))
        capturedLogEvents.exists(_.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Locked because it might be running on another instance") shouldBe true
      }
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(0)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and succeeds" in new Setup {
      val exception = new Exception("woopsy")
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(()))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(Future.successful(Right(Seq.empty)))) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        eventually {
          capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION.toString)) shouldBe true
        }
      }
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }

    s"return $Left ${MongoLockResponses.UnknownException} if lock returns exception, release lock is still called and failed also" in new Setup {
      val exception = new Exception("not again")
      when(mockLockRepository.takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration)))
        .thenReturn(Future.failed(exception))
      when(mockLockRepository.releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any()))
        .thenReturn(Future.failed(exception))
      withCaptureOfLoggingFrom(logger) { capturedLogEvents =>
        await(service.tryLock(Future.successful(Right(Seq.empty)))) shouldBe Left(MongoLockResponses.UnknownException(exception))
        capturedLogEvents.exists(event => event.getLevel.levelStr == "INFO" && event.getMessage == s"[$jobName] Failed with exception") shouldBe true
        eventually {
          capturedLogEvents.exists(_.getMessage.contains(PagerDutyKeys.MONGO_LOCK_UNKNOWN_EXCEPTION.toString)) shouldBe true
        }
      }
      verify(mockLockRepository, times(1)).takeLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any(), ArgumentMatchers.eq(releaseDuration))
      verify(mockLockRepository, times(1)).releaseLock(ArgumentMatchers.eq(mongoLockId), ArgumentMatchers.any())
    }
  }
}
