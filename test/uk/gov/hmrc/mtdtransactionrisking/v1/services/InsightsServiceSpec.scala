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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.InsightsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Insights, InsightsResponse, StrategicRisk}

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class InsightsServiceSpec extends AnyWordSpec with Matchers with MockitoSugar:

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val validVatNumber = "GB123456789"

  private val insightsRequest = InsightsRequest(vatRegistrationNumber = validVatNumber)

  private val insightsResponse: InsightsResponse =
    InsightsResponse(
      Insights(
        StrategicRisk(
          riskScore = 12.46,
          riskCorrelationId = CorrelationId("123e4567-e89b-12d3-a456-426614174000")
        )
      )
    )

  private def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

  class Test:
    val mockConnector: InsightsConnector = mock[InsightsConnector]
    val service = new InsightsService(mockConnector)

  "InsightsService" when:

    "assess is called with a valid VAT number" must:
      "return the risk response" in new Test:
        when(mockConnector.getRiskInsights(eqTo(insightsRequest))(any(), any()))
          .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, insightsResponse))))

        val result: ServiceOutcome[InsightsResponse] = await(service.assess(insightsRequest))
        result shouldBe Right(ResponseWrapper(correlationId, insightsResponse))

    "assess is called and the connector returns a JSON validation error" must:
      "return a Left with the ErrorWrapper" in new Test:
        when(mockConnector.getRiskInsights(eqTo(insightsRequest))(any(), any()))
          .thenReturn(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        val result: ServiceOutcome[InsightsResponse] = await(service.assess(insightsRequest))
        result shouldBe Left(ErrorWrapper(correlationId, DownstreamError))
