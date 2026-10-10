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

import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import java.util.concurrent.ThreadLocalRandom

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.*

trait NrsRetryPolicy:
  def maxRetries: Int
  def delayForRetry(retryNumber: Int): FiniteDuration
  def isExhaustedAfterFailure(failureCount: Int): Boolean

@Singleton
class ConfiguredNrsRetryPolicy @Inject()(appConfig: AppConfig) extends NrsRetryPolicy:

  override def maxRetries: Int =
    appConfig.nrsRetryMaxRetries

  def delayForRetry(retryNumber: Int): FiniteDuration =
    require(
      retryNumber >= 1 && retryNumber <= maxRetries,
      s"Retry number $retryNumber must be between 1 and $maxRetries"
    )

    require(
      appConfig.nrsRetryJitterFactor >= 0.0 &&
        appConfig.nrsRetryJitterFactor <= 1.0,
      "NRS retry jitter factor must be between 0.0 and 1.0"
    )

    val exponentialBackoffMillis =
      appConfig.nrsRetryInitialBackoff.toMillis *
        Math.pow(
          appConfig.nrsRetryBackoffMultiplier,
          retryNumber - 1
        )

    val cappedBackoffMillis =
      Math.min(
        exponentialBackoffMillis,
        appConfig.nrsRetryMaxBackoff.toMillis.toDouble
      )

    val jitterFactor =
      appConfig.nrsRetryJitterFactor

    val jitter =
      (ThreadLocalRandom.current().nextDouble() * 2 * jitterFactor) -
        jitterFactor

    val jitteredBackoffMillis =
      cappedBackoffMillis * (1.0 + jitter)

    Math.min(
      jitteredBackoffMillis,
      appConfig.nrsRetryMaxBackoff.toMillis.toDouble
    ).toLong.millis

  override def isExhaustedAfterFailure(retriesCompleted: Int): Boolean =
    retriesCompleted >= maxRetries
