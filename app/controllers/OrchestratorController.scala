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

import javax.inject.Inject
import models.notification.SDESNotification
import play.api.libs.json.{Json, Reads}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.NotificationMongoService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.Logger.logger

import scala.concurrent.{ExecutionContext, Future}

class OrchestratorController @Inject()(mongoService: NotificationMongoService,
                                       cc: ControllerComponents)(implicit ec: ExecutionContext) extends BackendController(cc) {

  def receiveSDESNotifications(): Action[AnyContent] = Action.async {
    implicit request => {
      request.body.asJson.fold({
        logger.error("[OrchestratorController][receiveSIDESNotifications] Failed to validate request body as JSON")
        Future(BadRequest("Invalid body received i.e. could not be parsed to JSON"))
      })(
        jsonBody => {
          val parseResultToModel = Json.fromJson(jsonBody)(Reads.seq(SDESNotification.apiReads))
          parseResultToModel.fold(
            failure => {
              logger.error("[OrchestratorController][receiveSIDESNotifications] Fail to parse request body to model")
              logger.debug(s"[OrchestratorController][receiveSIDESNotifications] Parse failure(s): $failure")
              Future(BadRequest("Failed to parse to model"))
            },
            notifications => {
              mongoService.insertNotificationRecordsIntoMongo(notifications).map {
                case true =>
                  logger.info(s"[OrchestratorController][receiveSDESNotifications] Successfully inserted ${notifications.size} notifications")
                  Ok("File Notification inserted")
                case false =>
                  logger.error(s"[OrchestratorController][receiveSIDESNotifications] Failed to insert File Notifications")
                  InternalServerError("Failed to insert File Notifications")
              } recover {
                case e =>
                  logger.error(s"[OrchestratorController][receiveSIDESNotifications] Unknown exception occurred with message: ${e.getMessage}")
                  InternalServerError("Something went wrong.")
              }
            }
          )
        }
      )
    }
  }
}
