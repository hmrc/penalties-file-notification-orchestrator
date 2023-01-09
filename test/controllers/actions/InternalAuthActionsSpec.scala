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

package controllers.actions
import base.SpecBase
import config.AppConfig
import config.featureSwitches.UseInternalAuth
import org.mockito.Matchers
import org.mockito.Mockito.{mock, reset, when}
import play.api.mvc.Results.Ok
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, Retrieval}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InternalAuthActionsSpec extends SpecBase {
  lazy val mockAuth: StubBehaviour = mock(classOf[StubBehaviour])
  val mockAppConfig: AppConfig = mock(classOf[AppConfig])
  implicit val cc: ControllerComponents = stubControllerComponents()
  lazy val authComponent: BackendAuthComponents = BackendAuthComponentsStub(mockAuth)

  object TestHarness extends InternalAuthActions {
    override def auth: BackendAuthComponents = authComponent

    override implicit val appConfig: AppConfig = mockAppConfig

    override def controllerComponents: ControllerComponents = cc
  }

  class Setup {
    reset(mockAuth)
    reset(mockAppConfig)
    when(mockAppConfig.isFeatureSwitchEnabled(Matchers.eq(UseInternalAuth))).thenReturn(true)
  }

  "authoriseService" when {
    "the feature switch is enabled" should {
      "authorise the caller" in new Setup {
        when(mockAuth.stubAuth(Matchers.any(), Matchers.any[Retrieval[Unit]])).thenReturn(Future.unit)
        val result: Future[Result] = TestHarness.authoriseService(_ => Ok("auth succeeded"))(FakeRequest().withHeaders("Authorization" -> "123"))
        status(result) shouldBe OK
        contentAsString(result) shouldBe "auth succeeded"
      }

      "return unauthorised when no/invalid token is provided" in new Setup {
        when(mockAuth.stubAuth(Matchers.any(), Matchers.any[Retrieval[Unit]])).thenReturn(Future.failed(UpstreamErrorResponse("UNAUTHORIZED", UNAUTHORIZED)))
        val result: Future[Result] = TestHarness.authoriseService(_ => Ok("auth succeeded"))(FakeRequest())
        status(result) shouldBe UNAUTHORIZED
      }

      "return forbidden when the token is valid but has incorrect permissions" in new Setup {
        when(mockAuth.stubAuth(Matchers.any(), Matchers.any[Retrieval[Unit]])).thenReturn(Future.failed(UpstreamErrorResponse("FORBIDDEN", FORBIDDEN)))
        val result: Future[Result] = TestHarness.authoriseService(_ => Ok("auth succeeded"))(FakeRequest().withHeaders("Authorization" -> "123"))
        status(result) shouldBe FORBIDDEN
      }
    }

    "the feature switch is disabled" should {
      "run the block" in new Setup {
        when(mockAppConfig.isFeatureSwitchEnabled(Matchers.eq(UseInternalAuth))).thenReturn(false)
        val result: Future[Result] = TestHarness.authoriseService(_ => Ok("auth succeeded"))(FakeRequest())
        status(result) shouldBe OK
        contentAsString(result) shouldBe "auth succeeded"
      }
    }

  }

}
