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

import base.SpecBase
import play.api.http.Status
import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status, stubControllerComponents}
import repositories.FileNotificationRepository

import scala.concurrent.{ExecutionContext, Future}

class MongoQueryControllerSpec extends SpecBase {
  val mockRepo: FileNotificationRepository = mock[FileNotificationRepository]
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  class Setup {
    reset(mockRepo)
    val controller = new MongoQueryController(stubControllerComponents(), mockRepo)
  }

  "getNumberOfRecords" should {
    s"return OK (${Status.OK}) with correct number of records" in new Setup {
      when(mockRepo.countAllRecords()).thenReturn(Future.successful(5))
      val result: Future[Result] = controller.getNumberOfRecords()(fakeRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe "5"
    }
  }
}
