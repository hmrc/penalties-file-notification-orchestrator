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

package services

import org.joda.time.Duration
import org.scalatest.matchers.should.Matchers._
import play.api.test.Helpers._
import repositories.LockRepositoryProvider
import uk.gov.hmrc.lock.LockRepository
import utils.IntegrationSpecCommonBase

import scala.concurrent.ExecutionContext.Implicits.global

class MonitoringJobISpec extends IntegrationSpecCommonBase {
  class Setup {
    val lockRepositoryProviderRepo: LockRepository = app.injector.instanceOf[LockRepositoryProvider].repo
    val service: MonitoringJobService = app.injector.instanceOf[MonitoringJobService]
    await(lockRepositoryProviderRepo.drop)
    await(lockRepositoryProviderRepo.ensureIndexes)
    await(lockRepositoryProviderRepo.count) shouldBe 0
  }

  "tryLock" should {
    "not do anything if the job is already locked" in new Setup {
      val randomServerId = "123"
      val releaseDuration = Duration.standardSeconds(123)
      await(lockRepositoryProviderRepo.count) shouldBe 0
      await(lockRepositoryProviderRepo.lock(service.lockKeeper.lockId, randomServerId, releaseDuration))
      await(lockRepositoryProviderRepo.count) shouldBe 1

      await(service.invoke).right.get shouldBe Seq(s"${service.jobName} - JobAlreadyRunning")
      await(lockRepositoryProviderRepo.count) shouldBe 1
    }
  }

  "invoke" should {
    "return a Right(Seq.empty)" in new Setup {
      val result = await(service.invoke)
      result.isRight shouldBe true
      result.right.get.isEmpty shouldBe true
    }
  }
}
