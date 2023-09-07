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

package connectors

import config.AppConfig
import models.notification.SDESNotification
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SDESConnector @Inject()(config: AppConfig,
                              httpClient: HttpClient) {

  def sendNotificationToSDES(notification: SDESNotification)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val sdesHeaders = Seq(
      "x-client-id" -> config.sdesOutboundBearerToken,
      "Content-Type" -> "application/json"
    )
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(config.sdesOutboundBearerToken)))
    httpClient.POST[SDESNotification, HttpResponse](
      url = config.sdesUrl,
      body = notification,
      headers = sdesHeaders
    )
  }
}
