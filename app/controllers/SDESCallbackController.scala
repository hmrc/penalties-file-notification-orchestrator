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

package controllers

import models.SDESCallback
import models.monitoring.FileNotificationStatusAuditModel
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.monitoring.AuditService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.Logger.logger

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SDESCallbackController @Inject()(auditService: AuditService, cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def handleCallback: Action[AnyContent] = Action.async {
    implicit request => {
      request.body.asJson.fold({
        logger.error("[SDESCallbackController][handleCallback] Failed to validate request body as JSON")
        Future(BadRequest("Invalid body received i.e. could not be parsed to JSON"))
      })(
        jsonBody => {
          val parseResultToModel = Json.fromJson(jsonBody)(SDESCallback.apiSDESCallbackReads)
          parseResultToModel.fold(
            failure => {
              logger.error("[SDESCallbackController][handleCallback] Fail to parse request body to model")
              logger.debug(s"[SDESCallbackController][handleCallback] Parse failure(s): $failure")
              Future(BadRequest("Failed to parse to model"))
            },
            sdesCallback => {
              logger.info(s"[SDESCallbackController][handleCallback] Callback received from SDES with status ${sdesCallback.notification}. SDESCallback received  = $sdesCallback.toString")
              auditService.audit(FileNotificationStatusAuditModel(sdesCallback.notification, sdesCallback.filename, sdesCallback.correlationID, sdesCallback.failureReason, sdesCallback.availableUntil))
              Future(NoContent)
            }
          )
        }
      )
    }
  }
}

