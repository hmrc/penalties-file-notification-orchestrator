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

package base

import config.AppConfig
import org.scalatest.matchers.should.{Matchers => ShouldMatchers}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.Injector
import play.api.mvc.AnyContent
import play.api.test.FakeRequest

class SpecBase extends AnyWordSpec with ShouldMatchers with GuiceOneAppPerSuite {

  lazy val injector: Injector = app.injector

  implicit val appConfig: AppConfig = injector.instanceOf[AppConfig]

  val fakeRequest: FakeRequest[AnyContent] = FakeRequest("GET", "/")

}
