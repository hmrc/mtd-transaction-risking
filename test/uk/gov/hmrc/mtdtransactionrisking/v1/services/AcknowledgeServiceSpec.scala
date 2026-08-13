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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

import scala.concurrent.Future

class AcknowledgeServiceSpec extends UnitSpec, MockitoSugar:

  implicit private val hc: HeaderCarrier = HeaderCarrier()
  implicit private val correlationId: CorrelationId = CorrelationId("test-correlation-id")

  private val request = AcknowledgeRequest(
    vrn = "123456789",
    reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
    correlationId = "9EEB55EF4FA9A24954BC982DF1D59B3D02BC097F6B1377B8B335C7583D92B959",
    presentedDateTime = "2026-06-09T10:30:00Z")

  private trait Test:
    val connector: AcknowledgeConnector = mock[AcknowledgeConnector]
    val service = new AcknowledgeStubService(connector)

  "acknowledge" should:

    "pass through a successful outcome from the connector" in new Test:
      when(connector.acknowledge(eqTo(request))(any(), any()))
        .thenReturn(Future.successful(Right(ResponseWrapper(correlationId, ()))))

      await(service.acknowledge(request)) shouldBe Right(ResponseWrapper(correlationId, ()))

    "pass through an error outcome from the connector" in new Test:
      val errorWrapper = ErrorWrapper(correlationId, DownstreamError, rawStatus = Some(BAD_REQUEST))
      when(connector.acknowledge(eqTo(request))(any(), any()))
        .thenReturn(Future.successful(Left(errorWrapper)))

      await(service.acknowledge(request)) shouldBe Left(errorWrapper)
