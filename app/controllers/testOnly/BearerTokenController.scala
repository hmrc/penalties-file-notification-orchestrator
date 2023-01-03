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

package controllers.testOnly

import play.api.Configuration
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject

class BearerTokenController @Inject()(cc: ControllerComponents, config: Configuration) extends BackendController(cc) {

  def getBearerToken(serviceName: String): Action[AnyContent] = Action {
    val optBearerToken = config.getOptional[String](s"$serviceName.outboundBearerToken")
    optBearerToken.fold(NotFound(s"Bearer token not found for service: $serviceName"))(Ok(_))
  }
}
