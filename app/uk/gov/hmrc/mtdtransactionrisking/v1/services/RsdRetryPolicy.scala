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

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.*

trait RsdRetryPolicy:
  def maxRetries: Int
  def delayForRetry(retryNumber: Int): FiniteDuration
  def isExhaustedAfterFailure(retriesCompleted: Int): Boolean

@Singleton
class ConfiguredRsdRetryPolicy @Inject()(
                                          appConfig: AppConfig
                                        ) extends RsdRetryPolicy:

  override def maxRetries: Int =
    appConfig.rsdRetryMaxRetries

  override def delayForRetry(
                              retryNumber: Int
                            ): FiniteDuration =
    require(
      retryNumber >= 1 && retryNumber <= maxRetries,
      s"Retry number $retryNumber must be between 1 and $maxRetries"
    )

    val calculatedMillis =
      appConfig.rsdRetryInitialBackoff.toMillis *
        Math.pow(
          appConfig.rsdRetryBackoffMultiplier,
          retryNumber - 1
        )

    Math.min(
      calculatedMillis,
      appConfig.rsdRetryMaxBackoff.toMillis.toDouble
    ).toLong.millis

  override def isExhaustedAfterFailure(
                                        retriesCompleted: Int
                                      ): Boolean =
    retriesCompleted >= maxRetries
