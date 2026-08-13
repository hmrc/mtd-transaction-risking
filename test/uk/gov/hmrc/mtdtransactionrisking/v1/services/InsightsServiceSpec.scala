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
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.connectors.MockInsightsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.InsightsRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Insights, InsightsResponse, StrategicRisk}

import scala.concurrent.Future

class InsightsServiceSpec extends UnitSpec, MockInsightsConnector:

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val insightsRequest = InsightsRequest(vatRegistrationNumber = "123456789")

  private val insightsResponse: InsightsResponse =
    InsightsResponse(
      Insights(
        StrategicRisk(
          riskCorrelationId = CorrelationId("123e4567-e89b-12d3-a456-426614174000"),
          riskScore = 12.46,
          reasons = Seq("VRN 'GB123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky.")
        )
      )
    )

  trait Test:
    val service = new InsightsService(mockInsightsConnector)

  "InsightsService.assess" when:

    "the connector returns a successful response" should:
      "return the risk response" in new Test:
        MockInsightsConnector
          .getRiskInsights(insightsRequest)
          .returns(Future.successful(Right(ResponseWrapper(correlationId, insightsResponse))))

        await(service.assess(insightsRequest)) shouldBe Right(ResponseWrapper(correlationId, insightsResponse))

    "the connector returns an error" should:
      "pass the ErrorWrapper through unchanged" in new Test:
        MockInsightsConnector
          .getRiskInsights(insightsRequest)
          .returns(Future.successful(Left(ErrorWrapper(correlationId, DownstreamError))))

        await(service.assess(insightsRequest)) shouldBe Left(ErrorWrapper(correlationId, DownstreamError))