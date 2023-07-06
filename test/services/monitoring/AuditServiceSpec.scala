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

package services.monitoring

import base.SpecBase
import config.AppConfig
import models.monitoring.JsonAuditModel
import org.mockito.ArgumentMatchers
import play.api.libs.json._
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import utils.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuditServiceSpec extends SpecBase with LogCapturing {
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockConfig: AppConfig = mock[AppConfig]

  val jsonAuditModel: JsonAuditModel = new JsonAuditModel{
    override val auditType = "testJsonAuditType"
    override val transactionName = "testJsonTransactionName"
    override val detail: JsObject = Json.obj("foo" -> "bar")
  }

  class Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    reset(mockAuditConnector)
    val service = new AuditService(mockConfig, mockAuditConnector)
    when(mockConfig.appName).thenReturn("penalties-file-notification-orchestrator")
    when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(AuditResult.Success))
  }

  "audit" should {
    "extract the data and pass it into the AuditConnector" in new Setup {
      val expectedData = service.toExtendedDataEvent(jsonAuditModel, "testUrl")
      service.audit(jsonAuditModel)(implicitly, implicitly, FakeRequest("POST", "testUrl"))
      verify(mockAuditConnector)
        .sendExtendedEvent(ArgumentMatchers.refEq(expectedData, "eventId", "generatedAt"))(
          ArgumentMatchers.any[HeaderCarrier],
          ArgumentMatchers.any[ExecutionContext]
        )
    }
  }

  "toExtendedDataEvent" should {
    "create and log the creation of the audit event" in new Setup {
      val result: ExtendedDataEvent = service.toExtendedDataEvent(jsonAuditModel, "/")
      result.detail shouldBe jsonAuditModel.detail
      result.auditType shouldBe jsonAuditModel.auditType
      result.auditSource shouldBe "penalties-file-notification-orchestrator"
    }
  }
}
