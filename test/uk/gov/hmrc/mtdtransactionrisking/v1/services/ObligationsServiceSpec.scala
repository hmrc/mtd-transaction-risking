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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.ObligationsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, TaxPeriodNotEndedError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.ObligationsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Obligation, ObligationDetail, ObligationsResponse}

import scala.concurrent.Future

class ObligationsServiceSpec extends UnitSpec, MockitoSugar:

  implicit private val hc: HeaderCarrier = HeaderCarrier()
  implicit private val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val vrn = "123456789"
  private val body = Json.obj("periodKey" -> "24AA")

  private val obligation = ObligationsResponse(
    Seq(
      Obligation(
        None,
        Seq(ObligationDetail("2000-01-01", "2000-01-31", "24AA"))
      )
    )
  )

  private trait Test:
    val connector: ObligationsConnector = mock[ObligationsConnector]
    val service = new ObligationsService(connector)

  "validate" should:

    "return Right(Unit) when the connector returns a successful obligation period" in new Test:
      when(connector.getObligations(eqTo(ObligationsRequest(vrn, "24AA")))(any(), any()))
        .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, obligation))))

      await(service.getObligations(ObligationsRequest(vrn, "24AA"))) shouldBe Right(ResponseWrapper(correlationId, obligation))

    "return Left(ErrorWrapper) when the period has not ended" in new Test:
      val errorWrapper = ErrorWrapper(correlationId, TaxPeriodNotEndedError)
      when(connector.getObligations(eqTo(ObligationsRequest(vrn, "24AA")))(any(), any()))
        .thenReturn(Future.successful(Left(errorWrapper)))

      await(service.getObligations(ObligationsRequest(vrn, "24AA"))) shouldBe Left(errorWrapper)

    "return Left(ErrorWrapper) when the connector returns a downstream error" in new Test:
      val errorWrapper = ErrorWrapper(correlationId, DownstreamError, rawStatus = Some(BAD_REQUEST))
      when(connector.getObligations(eqTo(ObligationsRequest(vrn, "24AA")))(any(), any()))
        .thenReturn(Future.successful(Left(errorWrapper)))

      await(service.getObligations(ObligationsRequest(vrn, "24AA"))) shouldBe Left(errorWrapper)
