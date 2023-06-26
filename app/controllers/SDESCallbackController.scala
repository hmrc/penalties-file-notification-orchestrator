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

package controllers

import models.SDESCallback
import models.SDESFileNotificationEnum._
import models.monitoring.FileNotificationStatusAuditModel
import models.notification.RecordStatusEnum._
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.HandleCallbackService
import services.monitoring.AuditService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.Logger.logger
import utils.PagerDutyHelper
import utils.PagerDutyHelper.PagerDutyKeys._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SDESCallbackController @Inject()(auditService: AuditService,
                                       handleCallbackService: HandleCallbackService,
                                       cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def handleCallback: Action[AnyContent] = Action.async {
    implicit request => {
      request.body.asJson.fold({
        PagerDutyHelper.log("handleCallback", FAILED_TO_VALIDATE_REQUEST_AS_JSON)
        logger.error("[SDESCallbackController][handleCallback] Failed to validate request body as JSON")
        Future(BadRequest("Invalid body received i.e. could not be parsed to JSON"))
      })(
        jsonBody => {
          val parseResultToModel = Json.fromJson(jsonBody)(SDESCallback.apiSDESCallbackReads)
          parseResultToModel.fold(
            failure => {
              PagerDutyHelper.log("handleCallback", FAILED_TO_PARSE_REQUEST_TO_MODEL)
              logger.error("[SDESCallbackController][handleCallback] Fail to parse request body to model")
              logger.debug(s"[SDESCallbackController][handleCallback] Parse failure(s): $failure")
              Future(BadRequest("Failed to parse to model"))
            },
            sdesCallback => {
              logger.debug(s"[SDESCallbackController][handleCallback] Callback received from SDES with status ${sdesCallback.notification}. SDESCallback received = ${sdesCallback.toString}")
              logger.info(s"[SDESCallbackController][handleCallback] Callback received from SDES with status ${sdesCallback.notification}. Correlation ID: ${sdesCallback.correlationID}")
              auditService.audit(FileNotificationStatusAuditModel(sdesCallback.notification, sdesCallback.filename, sdesCallback.correlationID, sdesCallback.failureReason, sdesCallback.availableUntil))
              val notificationStatus = sdesCallback.notification match {
                case FileReceived => FILE_RECEIVED_IN_SDES
                case FileProcessed | FileReady => FILE_PROCESSED_IN_SDES
                case FileProcessingFailure => NOT_PROCESSED_PENDING_RETRY
              }
              if(sdesCallback.notification == FileProcessingFailure) {
                PagerDutyHelper.log("handleCallback", FAILED_TO_PROCESS_FILE_NOTIFICATION)
              }
              //Correlation ID maps to reference (at time of writing: 23/06/23)
              handleCallbackService.updateNotificationAfterCallback(sdesCallback.correlationID, notificationStatus).map {
                _.fold(
                  InternalServerError(_),
                  _ => NoContent
                )
              }
            }
          )
        }
      )
    }
  }
}

