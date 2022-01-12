
package controllers

import javax.inject.Inject
import models.notification.SDESNotification
import play.api.libs.json.{Json, Reads}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import repositories.FileNotificationRepositories
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.Logger.logger

import scala.concurrent.{ExecutionContext, Future}

class OrchestratorController @Inject()(repository: FileNotificationRepositories,
                                       cc: ControllerComponents)(implicit ec: ExecutionContext) extends BackendController(cc) {

  def receiveSIDESNotifications(): Action[AnyContent] = Action.async {
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
              repository.storeFileNotifications(notifications).map {
                response =>
                  response.status match {
                    case OK =>
                      Ok("")
                    case _ =>
                      logger.error(s"[OrchestratorController][receiveSIDESNotifications] Mongo returned unknown status code: ${response.status} ")
                      logger.debug(s"[OrchestratorController][receiveSIDESNotifications] Failure response body: ${response.body}")
                      Status(response.status)
                  }
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
