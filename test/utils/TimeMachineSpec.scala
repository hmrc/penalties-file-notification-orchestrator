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

package utils

import base.SpecBase

import java.time.{Instant, LocalDateTime}

class TimeMachineSpec extends SpecBase {
  val timeMachine: TimeMachine = injector.instanceOf[TimeMachine]
  "dateTimeNow" should {
    "return the systems date time" in {
      val nowMinusAFewMillis: LocalDateTime = LocalDateTime.now().minusNanos(100)
      timeMachine.dateTimeNow.isAfter(nowMinusAFewMillis) shouldBe true
    }
  }

  "now" should {
    "return the systems date time (as an instant)" in {
      val nowMinusAFewMillis: Instant = Instant.now().minusNanos(100)
      timeMachine.now.isAfter(nowMinusAFewMillis) shouldBe true
    }
  }
}
