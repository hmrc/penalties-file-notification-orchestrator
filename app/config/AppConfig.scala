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

package config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(config: Configuration, servicesConfig: ServicesConfig) {

  val appName: String = servicesConfig.getString("appName")

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  lazy val useStubForSDESCall: Boolean = config.get[Boolean]("feature-switch.useStubForSDESCall")

  lazy val urlHeaderAuthorisation: String = s"Bearer ${config.get[String]("sdes.outboundBearerToken")}"

  lazy val srn: String = config.get[String]("sdes.srn")

  private val sdesBaseUrl: String = {
    if (useStubForSDESCall) servicesConfig.baseUrl("penalties-stub") + "/penalties-stub"
    else servicesConfig.baseUrl("sdes")
  }

  val sdesUrl: String = sdesBaseUrl + s"/notification/fileready"

  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String     = config.get[String]("microservice.metrics.graphite.host")

  val notificationTtl:Long = config.get[Long]("mongo-config.ttlHours")


}
