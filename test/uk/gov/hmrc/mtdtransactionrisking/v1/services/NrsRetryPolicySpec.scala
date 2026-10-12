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

    def configuredMaxRetries: Int = 10

    def configuredInitialBackoff = 10.minutes

    def configuredBackoffMultiplier: Double = 2.0

    def configuredMaxBackoff = 5120.minutes

    def configuredJitterFactor: Double = 0.10

    MockedAppConfig.nrsRetryMaxRetries
      .returns(configuredMaxRetries)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryInitialBackoff
      .returns(configuredInitialBackoff)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryBackoffMultiplier
      .returns(configuredBackoffMultiplier)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryMaxBackoff
      .returns(configuredMaxBackoff)
      .anyNumberOfTimes()

    MockedAppConfig.nrsRetryJitterFactor
      .returns(configuredJitterFactor)
      .anyNumberOfTimes()

    val policy =
      new ConfiguredNrsRetryPolicy(mockAppConfig)

  "NrsRetryPolicy.delayForRetry" should:

    "produce the configured exponential retry sequence when jitter is disabled" in new Test:

      override def configuredJitterFactor: Double =
        0.0

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

    "apply jitter within the configured plus or minus ten percent range" in new Test:

      val baseDelay =
        10.minutes

      (1 to 100).foreach { _ =>
        val actualDelay =
          policy.delayForRetry(1)

        actualDelay.toMillis should be >=
          (baseDelay.toMillis * 0.90).toLong

        actualDelay.toMillis should be <=
          (baseDelay.toMillis * 1.10).toLong
      }

    "never exceed the configured maximum backoff" in new Test:

        (1 to policy.maxRetries).foreach { retryNumber =>
          policy.delayForRetry(retryNumber).toMillis should be <=
            configuredMaxBackoff.toMillis
        }

    "cap exponential backoff when it exceeds the configured maximum" in new Test:
      override def configuredMaxRetries: Int = 11

      override def configuredMaxBackoff = 160.minutes

      override def configuredJitterFactor: Double = 0.0

      policy.delayForRetry(6) shouldBe 160.minutes

    "reject a negative jitter factor" in new Test:

      override def configuredJitterFactor: Double =
        -0.01

      intercept[IllegalArgumentException] {
        policy.delayForRetry(1)
      }

    "reject a jitter factor greater than one" in new Test:

      override def configuredJitterFactor: Double =
        1.01

      intercept[IllegalArgumentException] {
        policy.delayForRetry(1)
      }

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
