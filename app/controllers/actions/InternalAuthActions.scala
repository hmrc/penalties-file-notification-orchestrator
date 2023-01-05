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

import config.AppConfig
import config.featureSwitches.{FeatureSwitching, UseInternalAuth}
import models.auth.AuthRequest
import uk.gov.hmrc.http.Authorization
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import utils.Logger

import scala.concurrent.{ExecutionContext, Future}

trait InternalAuthActions extends FeatureSwitching {

  def auth: BackendAuthComponents

  implicit val appConfig: AppConfig

  def controllerComponents: ControllerComponents

  private def permission = Predicate.Permission(
    Resource(
      ResourceType("penalties-file-notification-orchestrator"),
      ResourceLocation("*")
    ),
    IAAction("WRITE")
  )

  def authoriseService: ActionBuilder[Request, AnyContent] = {
    if(isEnabled(UseInternalAuth)) {
      Logger.logger.debug("[InternalAuthActions][authoriseService] - Internal auth is enabled")
      auth.authorizedAction(
        permission,
        onUnauthorizedError = Future.successful(Unauthorized("Request was unauthenticated or expired")),
        onForbiddenError = Future.successful(Forbidden("Request was authenticated but failed authorisation predicates"))
      )
    } else {
      Logger.logger.debug("[InternalAuthActions][authoriseService] - Internal auth is disabled")
      new ActionBuilder[({type A[B] = AuthRequest[B]})#A, AnyContent] {
        override def parser: BodyParser[AnyContent] = controllerComponents.parsers.default

        override protected def executionContext: ExecutionContext = controllerComponents.executionContext

        override def invokeBlock[A](request: Request[A], block: AuthRequest[A] => Future[Result]): Future[Result] = {
          val headerCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
          block(AuthRequest(request, headerCarrier, Authorization("Bearer 123")))
        }
      }
    }

  }
}
