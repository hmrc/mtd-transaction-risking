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

import cats.data.EitherT
import cats.implicits.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.RdsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest
import uk.gov.hmrc.mtdtransactionrisking.v1.services.auth.RdsAuthService
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AcknowledgeService @Inject() (rdsAuthService: RdsAuthService, rdsConnector: RdsConnector, interactionService: InteractionService, acknowledgeConnector: AcknowledgeConnector)(implicit ec: ExecutionContext) extends Logging:

  def stubAcknowledge(request: AcknowledgeRequest)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Unit]] =
    acknowledgeConnector.acknowledge(request)

  def acknowledge(request: AcknowledgeRequest)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Unit]] =
    logger.info(s"${correlationId.value}::[AcknowledgeService][acknowledge] acknowledgement received for reportId ${request.reportId}")
    interactionService.storeAcknowledgement(request)
    val result = for
      credentials <- EitherT(rdsAuthService.bearerToken())
      _           <- EitherT(rdsConnector.acknowledge(request, credentials.responseData))
    yield ResponseWrapper(correlationId, ())

    result.value