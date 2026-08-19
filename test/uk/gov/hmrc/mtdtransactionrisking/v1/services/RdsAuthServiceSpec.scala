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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.support.{MockAppConfig, UnitSpec}
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.MockRdsAuthConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.RdsAuthCredentials
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.services.auth.RdsAuthService

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RdsAuthServiceSpec extends UnitSpec, MockRdsAuthConnector, MockAppConfig:

  implicit val hc: HeaderCarrier            = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val issuedAt = Instant.parse("2026-08-13T09:00:00Z")

  /** tokens live for roughly four hours; the service refreshes five minutes early. */
  private val tokenLifetimeSeconds = 14399
  private val refreshMarginSeconds = 300

  private val credentials          = RdsAuthCredentials("a-bearer-token", "bearer", tokenLifetimeSeconds)
  private val refreshedCredentials = credentials.copy(access_token = "a-refreshed-token")

  private val justBeforeRefresh = issuedAt.plusSeconds(tokenLifetimeSeconds - refreshMarginSeconds - 1)
  private val atRefreshMargin   = issuedAt.plusSeconds(tokenLifetimeSeconds - refreshMarginSeconds)
  private val afterExpiry       = issuedAt.plusSeconds(tokenLifetimeSeconds)

  /** A clock the test can move forward so token expiry is can be manipulated. */
  private class TestClock(var now: Instant) extends Clock:
    override def instant(): Instant              = now
    override def getZone: ZoneId                 = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock   = this

  private trait Test:
    val testClock = new TestClock(issuedAt)

    val service = new RdsAuthService(mockRdsAuthConnector, mockAppConfig, testClock)

    def authIsRequired(): Unit =
      MockedAppConfig.rdsAuthRequired.returns(true).anyNumberOfTimes()

    def authIsNotRequired(): Unit =
      MockedAppConfig.rdsAuthRequired.returns(false).anyNumberOfTimes()

    /** Expected exactly once, so an unnecessary refetch fails the test. */
    def connectorReturns(credentials: RdsAuthCredentials): Unit =
      MockRdsAuthConnector.retrieveBearerToken
        .returns(Future.successful(Right(ResponseWrapper(correlationId, credentials))))
        .once()

    def connectorFails(): Unit =
      MockRdsAuthConnector.retrieveBearerToken
        .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))
        .once()

  "bearerToken" when:

    "the environment does not require auth" should:
      "return no credentials without calling the connector" in new Test:
        authIsNotRequired()
        // No connector expectation — no token should be requested

        await(service.bearerToken()) shouldBe Right(ResponseWrapper(correlationId, None))

    "no token has been cached" should:

      "fetch one from the connector" in new Test:
        authIsRequired()
        connectorReturns(credentials)

        await(service.bearerToken()) shouldBe Right(ResponseWrapper(correlationId, Some(credentials)))

      "pass the connector's error through" in new Test:
        authIsRequired()
        connectorFails()

        await(service.bearerToken()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))

    "a cached token is still valid" should:

      "reuse it rather than fetching another" in new Test:
        authIsRequired()
        connectorReturns(credentials)

        await(service.bearerToken())

        await(service.bearerToken()) shouldBe Right(ResponseWrapper(correlationId, Some(credentials)))

      "reuse it up to a second before the refresh margin" in new Test:
        authIsRequired()
        connectorReturns(credentials)

        await(service.bearerToken())

        testClock.now = justBeforeRefresh

        await(service.bearerToken()) shouldBe Right(ResponseWrapper(correlationId, Some(credentials)))

    "the cached token has reached its refresh margin" should:

      "fetch a fresh one" in new Test:
        authIsRequired()
        connectorReturns(credentials)

        await(service.bearerToken())

        testClock.now = atRefreshMargin
        connectorReturns(refreshedCredentials)

        await(service.bearerToken()) shouldBe Right(ResponseWrapper(correlationId, Some(refreshedCredentials)))

      "return the error when the refresh fails" in new Test:
        authIsRequired()
        connectorReturns(credentials)

        await(service.bearerToken())

        testClock.now = afterExpiry
        connectorFails()

        await(service.bearerToken()) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))