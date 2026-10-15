/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.mtdtransactionrisking.v1.services

import uk.gov.hmrc.mtdtransactionrisking.support.{MockAppConfig, UnitSpec}

import scala.concurrent.duration.*

class RsdRetryPolicySpec extends UnitSpec, MockAppConfig:

  private trait Test:
    MockedAppConfig.rsdRetryMaxRetries
      .returns(10)
      .anyNumberOfTimes()

    MockedAppConfig.rsdRetryInitialBackoff
      .returns(5.minutes)
      .anyNumberOfTimes()

    MockedAppConfig.rsdRetryBackoffMultiplier
      .returns(2.0)
      .anyNumberOfTimes()

    MockedAppConfig.rsdRetryMaxBackoff
      .returns(2560.minutes)
      .anyNumberOfTimes()

    val policy =
      new ConfiguredRsdRetryPolicy(
        appConfig = mockAppConfig
      )

  "RsdRetryPolicy.delayForRetry" should:

    "return the exact DL-20467 retry schedule with no jitter" in new Test:
      (1 to 10).map(
        policy.delayForRetry
      ) shouldBe Seq(
        5.minutes,
        10.minutes,
        20.minutes,
        40.minutes,
        80.minutes,
        160.minutes,
        320.minutes,
        640.minutes,
        1280.minutes,
        2560.minutes
      )

    "reject retry zero" in new Test:
      intercept[IllegalArgumentException] {
        policy.delayForRetry(0)
      }

    "reject retry eleven" in new Test:
      intercept[IllegalArgumentException] {
        policy.delayForRetry(11)
      }

  "RsdRetryPolicy.isExhaustedAfterFailure" should:

    "allow a further retry after retry nine has not yet failed" in new Test:
      policy.isExhaustedAfterFailure(9) shouldBe false

    "mark retry ten as exhausted after it fails" in new Test:
      policy.isExhaustedAfterFailure(10) shouldBe true

    "keep retry counts above ten exhausted" in new Test:
      policy.isExhaustedAfterFailure(11) shouldBe true
