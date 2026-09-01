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

package uk.gov.hmrc.mtdtransactionrisking.v1.services.auth

import cats.implicits.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.RdsAuthConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.RdsAuthCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import java.time.{Clock, Duration, Instant}
import java.util.concurrent.atomic.AtomicReference
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RdsAuthService @Inject() (connector: RdsAuthConnector, appConfig: AppConfig, clock: Clock)(implicit ec: ExecutionContext) extends Logging:

  private val refreshMargin = Duration.ofMinutes(5)

  private val cachedToken = new AtomicReference[Option[CachedToken]](None)

  /** The bearer token for RDS calls, None in environments that don't require auth. The token lives for four hours and is reused until shortly before
    * it expires. SAS ask that callers avoid fetching one per request.
    */
  def bearerToken()(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Option[RdsAuthCredentials]]] =
    if !appConfig.rdsAuthRequired then Future.successful(Right(ResponseWrapper(correlationId, None)))
    else cachedOrFresh()

  private def cachedOrFresh()(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Option[RdsAuthCredentials]]] =
    val now = Instant.now(clock)

    cachedToken.get().filter(_.isValid(now)) match
      case Some(token) =>
        Future.successful(Right(ResponseWrapper(correlationId, Some(token.credentials))))

      case None =>
        connector.retrieveBearerToken().map {
          case Right(ResponseWrapper(_, credentials)) =>
            cachedToken.set(Some(CachedToken(credentials, credentials.expiresAt(now, refreshMargin))))
            Right(ResponseWrapper(correlationId, Some(credentials)))

          case Left(errorWrapper) =>
            Left(errorWrapper)
        }

  private case class CachedToken(credentials: RdsAuthCredentials, expiresAt: Instant):
    def isValid(now: Instant): Boolean = now.isBefore(expiresAt)
