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

class NrsRetryPolicySpec extends UnitSpec, MockAppConfig:

  private trait Test:

    MockedAppConfig.nrsRetryMaxRetries
      .returns(10)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryInitialBackoff
      .returns(10.minutes)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryBackoffMultiplier
      .returns(2.0)
      .anyNumberOfTimes()

    val policy =
      new ConfiguredNrsRetryPolicy(mockAppConfig)

  "NrsRetryPolicy.delayForRetry" should:

    "produce the configured exponential retry sequence" in new Test:
      val expected =
        Seq(
          10.minutes,
          20.minutes,
          40.minutes,
          80.minutes,
          160.minutes,
          320.minutes,
          640.minutes,
          1280.minutes,
          2560.minutes,
          5120.minutes
        )

      val actual =
        (1 to 10).map(policy.delayForRetry)

      actual shouldBe expected

    "reject a retry number below one" in new Test:
      intercept[IllegalArgumentException] {
        policy.delayForRetry(0)
      }

    "reject a retry number greater than the configured maximum" in new Test:
      intercept[IllegalArgumentException] {
        policy.delayForRetry(11)
      }

  "NrsRetryPolicy.isExhaustedAfterFailure" should:

    "return false while retries remain" in new Test:
      policy.isExhaustedAfterFailure(9) shouldBe false

    "return true when the maximum retry count is reached" in new Test:
      policy.isExhaustedAfterFailure(10) shouldBe true

    "return true when the failure count exceeds the maximum retry count" in new Test:
      policy.isExhaustedAfterFailure(11) shouldBe true

    "return the configured initial backoff for retry 1" in new Test:
      policy.delayForRetry(1) shouldBe 10.minutes
