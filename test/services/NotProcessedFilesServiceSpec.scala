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

import java.time.LocalDateTime

import base.SpecBase
import connectors.SDESConnector
import models.SDESNotificationRecord
import models.notification.{RecordStatusEnum, SDESAudit, SDESChecksum, SDESNotification, SDESNotificationFile, SDESProperties}
import org.mockito.Matchers
import org.mockito.Mockito.{mock, reset, times, verify, when}
import play.api.Configuration
import play.api.test.Helpers.{NO_CONTENT, await, defaultAwaitTimeout}
import repositories.FileNotificationRepository
import scheduler.ScheduleStatus
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import utils.{LogCapturing, TimeMachine}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class NotProcessedFilesServiceSpec extends SpecBase with LogCapturing {
  val mockLockRepository: MongoLockRepository = mock(classOf[MongoLockRepository])
  val mockSDESConnector: SDESConnector = mock(classOf[SDESConnector])
  val mockConfig: Configuration = mock(classOf[Configuration])
  val mockTimeMachine: TimeMachine = mock(classOf[TimeMachine])
  val mockFileNotificationRepository: FileNotificationRepository = mock(classOf[FileNotificationRepository])
  val jobName = "NotProcessedFilesService"

  val mongoLockId: String = s"schedules.$jobName"
  val mongoLockTimeout: Int = 123
  val releaseDuration: Duration = mongoLockTimeout.seconds

  val mockDateTime: LocalDateTime = LocalDateTime.of(2022, 1, 1, 0, 0, 0)
  val notification: SDESNotification = SDESNotification(
    informationType = "info",
    file = SDESNotificationFile(
      recipientOrSender = "penalties",
      name = "file1.txt",
      location = "http://example.com",
      checksum = SDESChecksum(algorithm = "SHA-256", value = "123456789-abcdef-123456789"),
      size = 256,
      properties = Seq.empty[SDESProperties]
    ),
    audit = SDESAudit("file 1")
  )


  val notificationRecord: SDESNotificationRecord = SDESNotificationRecord(
    reference = "ref1",
    status = RecordStatusEnum.PENDING,
    numberOfAttempts = 1,
    createdAt = mockDateTime.minusHours(1),
    updatedAt = mockDateTime,
    nextAttemptAt = mockDateTime,
    notification = notification
  )

  val pendingNotifications: Seq[SDESNotificationRecord] = Seq(
    notificationRecord,
    notificationRecord.copy(reference= "ref2", nextAttemptAt = mockDateTime.minusSeconds(1)),
    notificationRecord.copy(reference = "ref3", nextAttemptAt = mockDateTime.plusSeconds(1))
  )

  class Setup(withMongoLockStubs: Boolean = true) {
    reset(mockLockRepository, mockConfig, mockSDESConnector, mockFileNotificationRepository, mockTimeMachine, mockSDESConnector)
    val service = new NotProcessedFilesService(mockLockRepository, mockFileNotificationRepository, mockTimeMachine, mockConfig, appConfig)
    when(mockConfig.get[Int](Matchers.eq(s"schedules.${service.jobName}.mongoLockTimeout"))(Matchers.any()))
      .thenReturn(mongoLockTimeout)
    when(mockTimeMachine.now).thenReturn(mockDateTime.plusMinutes(appConfig.configurableTimeMinutes))
    if (withMongoLockStubs) {
      when(mockLockRepository.takeLock(Matchers.eq(mongoLockId), Matchers.any(), Matchers.eq(releaseDuration)))
        .thenReturn(Future.successful(true))
      when(mockLockRepository.releaseLock(Matchers.eq(mongoLockId), Matchers.any()))
        .thenReturn(Future.successful(()))
    }
  }

  "invoke" should {
    "run the job successfully if there are no relevant notifications" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(Seq.empty))
      when(mockFileNotificationRepository.getFilesReceivedBySDES()).thenReturn(Future.successful(Seq.empty))
      val result: Either[ScheduleStatus.JobFailed, String] = await(service.invoke)
      result.isRight shouldBe true
      result.getOrElse("fail") shouldBe "Processed all notifications"
    }

    "process the notifications and return Right if they all succeed - only process if nextAttemptAt is < now" in new Setup {
      when(mockFileNotificationRepository.getPendingNotifications()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.getFilesReceivedBySDES()).thenReturn(Future.successful(pendingNotifications))
      when(mockFileNotificationRepository.updateFileNotification(Matchers.any())).thenReturn(Future.successful(
        notificationRecord.copy(reference = "ref2", status = RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY, updatedAt = LocalDateTime.now())
      ))
      when(mockSDESConnector.sendNotificationToSDES(Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.getOrElse("fail") shouldBe "Processed all notifications"
      mockFileNotificationRepository.countRecordsByStatus(RecordStatusEnum.NOT_PROCESSED_PENDING_RETRY) shouldBe 1
    }
  }
}
