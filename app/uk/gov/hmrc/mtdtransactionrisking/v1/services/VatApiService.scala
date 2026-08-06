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

import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.VatApiConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{DownstreamError, ErrorWrapper, ServiceUnavailableError, TaxPeriodNotEndedError}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.outcomes.ResponseWrapper
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{Obligation, ObligationPeriod, ObligationsResponse}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatApiService @Inject() (connector: VatApiConnector)(implicit ec: ExecutionContext):

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  def validate(vrn: String, body: JsValue)(implicit hc: HeaderCarrier, correlationId: CorrelationId): Future[ServiceOutcome[Obligation]] =
    connector.validate(vrn, body)

  def getObligationPeriod(periodKey: String, vrn: String, today: LocalDate = LocalDate.now())(implicit
      hc: HeaderCarrier,
      correlationId: CorrelationId
  ): Future[ServiceOutcome[ObligationPeriod]] =
    connector.getObligations(vrn).map {
      case Right(ResponseWrapper(corrId, obligationsResponse)) =>
        findOpenObligationPeriod(obligationsResponse, periodKey, today) match
          case Left(TaxPeriodNotEndedError) =>
            Left(ErrorWrapper(corrId, TaxPeriodNotEndedError))
          case Right(Some(obligationPeriod)) =>
            Right(ResponseWrapper(corrId, obligationPeriod))
          case Right(None) =>
            Left(ErrorWrapper(corrId, DownstreamError))

      case Left(errorWrapper) =>
        Left(ErrorWrapper(errorWrapper.correlationId, ServiceUnavailableError))
    }

  private def findOpenObligationPeriod(
      response: ObligationsResponse,
      periodKey: String,
      today: LocalDate
  ): Either[TaxPeriodNotEndedError.type, Option[ObligationPeriod]] =
    response.obligations.find(_.periodKey == periodKey) match
      case Some(obligation) =>
        val toDate = LocalDate.parse(obligation.end, dateFormatter)
        if today.isAfter(toDate) then Right(Some(ObligationPeriod(obligation.start, obligation.end)))
        else Left(TaxPeriodNotEndedError)
      case None =>
        Right(None)
