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

package config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(config: Configuration, servicesConfig: ServicesConfig) {

  val appName: String = servicesConfig.getString("appName")

  lazy val useStubForSDESCall: Boolean = config.get[Boolean]("feature-switch.useStubForSDESCall")

  lazy val sdesOutboundBearerToken: String = config.get[String]("sdes.outboundBearerToken")

  lazy val sdesBaseUrl: String = {
    if (useStubForSDESCall) servicesConfig.baseUrl("penalties-stub") + "/penalties-stub"
    else servicesConfig.baseUrl("sdes")
  }

  lazy val sdesUrl: String = sdesBaseUrl + s"/notification/fileready"

  lazy val notificationTtl: Long = config.get[Long]("mongo-config.ttlDays")

  lazy val minutesUntilNextAttemptOnCallbackFailure: Int = config.get[Int]("notifications.minutesUntilRetryOnCallbackFailure")

  lazy val numberOfMinutesToWaitUntilNotificationRetried: Int = config.get[Int]("notifications.numberOfMinutesToWaitUntilNotificationRetried")

  lazy val numberOfNotificationsToSendInBatch: Int = config.get[Int]("notifications.numberOfNotificationsToSendInBatch")
}
