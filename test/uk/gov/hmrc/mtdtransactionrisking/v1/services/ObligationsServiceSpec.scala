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

import controllers.Execution.trampoline
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalactic.Prettifier.default
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.ObligationsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, TaxPeriodNotEndedError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Obligation, ObligationPeriod, ObligationsResponse}

import java.time.LocalDate
import scala.concurrent.Future
class ObligationsServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {


  val mockConnector: ObligationsConnector = mock[ObligationsConnector]
  val service = new ObligationsService(mockConnector)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  val periodKey = "18A"
  val vrn = "123456789"


  val today: LocalDate = LocalDate.now()
  val pastDate: String = today.minusDays(5).toString
  val futureDate: String = today.plusDays(5).toString


  def createObligationsResponse(toDate: String): ObligationsResponse = {
    ObligationsResponse(
      obligations = Seq(
        Obligation(
          status = "O",
          start = "2026-01-01",
          end = toDate,
          due = "2026-03-07",
          periodKey = periodKey
        )
      )
    )
  }
  

  "getObligations" should {

    "return a successful ResponseWrapper with ObligationPeriod when the period end date is in the past" in {
      val rawResponse = createObligationsResponse(toDate = pastDate)
      when(mockConnector.getObligations(any())(any(), any()))
        .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, rawResponse))))

      val result = service.getObligations(periodKey, vrn)

      result.map { outcome =>
        outcome shouldBe Right(ResponseWrapper(correlationId, ObligationPeriod("2026-01-01", pastDate)))
      }
    }

    "return an ErrorWrapper with TaxPeriodNotEndedError when the period end date is in the future" in {
      val rawResponse = createObligationsResponse(toDate = futureDate)
      when(mockConnector.getObligations(any())(any(), any()))
        .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, rawResponse))))

      val result = service.getObligations(periodKey, vrn)

      result.map { outcome =>
        outcome shouldBe Left(ErrorWrapper(correlationId, TaxPeriodNotEndedError))
      }
    }

    "return an ErrorWrapper with TaxPeriodNotEndedError when the period end date is exactly today" in {
      val rawResponse = createObligationsResponse(toDate = today.toString)
      when(mockConnector.getObligations(any())(any(), any()))
        .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, rawResponse))))

      val result = service.getObligations(periodKey, vrn)

      result.map { outcome =>
        outcome shouldBe Left(ErrorWrapper(correlationId, TaxPeriodNotEndedError))
      }
    }

    "return an ErrorWrapper with DownstreamError when the requested periodKey does not exist in the response data" in {
      val rawResponse = createObligationsResponse(toDate = pastDate)
      when(mockConnector.getObligations(any())(any(), any()))
        .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, rawResponse))))


      val result = service.getObligations("99X", vrn)

      result.map { outcome =>
        outcome shouldBe Left(ErrorWrapper(correlationId, DownstreamError))
      }
    }

    "pass through the upstream ErrorWrapper cleanly when the connector fails" in {
      val upstreamError = ErrorWrapper(correlationId, DownstreamError)
      when(mockConnector.getObligations(any())(any(), any()))
        .thenReturn(Future.successful(Left(upstreamError)))

      val result = service.getObligations(periodKey, vrn)

      result.map { outcome =>
        outcome shouldBe Left(upstreamError)
      }
    }
  }
}