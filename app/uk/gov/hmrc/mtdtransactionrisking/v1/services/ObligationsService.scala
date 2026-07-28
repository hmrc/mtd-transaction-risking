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
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.ObligationsConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, TaxPeriodNotEndedError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{ObligationPeriod, ObligationsResponse}
import uk.gov.hmrc.mtdtransactionrisking.v1.services.ServiceOutcome

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ObligationsService @Inject() (connector: ObligationsConnector)(implicit ec: ExecutionContext):

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def getObligations(periodKey: String, VRN: String)(implicit
      hc: HeaderCarrier,
      correlationId: CorrelationId): Future[ServiceOutcome[ObligationPeriod]] =
    connector.getObligations(VRN).map {
      case Right(ResponseWrapper(corrId, obligationsResponse)) =>
        findOpenObligationPeriod(obligationsResponse, periodKey) match
          case Left(TaxPeriodNotEndedError) =>
            Left(ErrorWrapper(corrId, TaxPeriodNotEndedError))
          case Right(Some(obligationPeriod)) =>
            Right(ResponseWrapper(corrId, obligationPeriod))
          case Right(None) =>
            Left(ErrorWrapper(corrId, DownstreamError))

      case Left(errorWrapper) =>
        Left(errorWrapper)
    }

  private def findOpenObligationPeriod(response: ObligationsResponse,
                                       periodKey: String): Either[TaxPeriodNotEndedError.type, Option[ObligationPeriod]] =
    response.obligations.iterator
      .flatMap(_.obligationDetails.iterator)
      .find(_.periodKey == periodKey) match
      case Some(detail) =>
        val toDate = LocalDate.parse(detail.inboundCorrespondenceToDate, dateFormatter)
        if LocalDate.now().isAfter(toDate) then
          Right(Some(ObligationPeriod(detail.inboundCorrespondenceFromDate, detail.inboundCorrespondenceToDate)))
        else Left(TaxPeriodNotEndedError)
      case None =>
        Right(None)
