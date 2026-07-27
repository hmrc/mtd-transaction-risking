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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.response

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.*
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.TaxPeriodNotEndedError

import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class ObligationsResponse(obligations: Seq[Obligation]):

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def findOpenObligationPeriod(periodKey: String): Either[TaxPeriodNotEndedError.type, Option[ObligationPeriod]] =
    obligations.iterator
      .flatMap(_.obligationDetails.iterator)
      .find(_.periodKey == periodKey) match
      case Some(detail) =>
        val toDate = LocalDate.parse(detail.inboundCorrespondenceToDate, dateFormatter)
        if LocalDate.now().isAfter(toDate) then
          Right(Some(ObligationPeriod(detail.inboundCorrespondenceFromDate, detail.inboundCorrespondenceToDate)))
        else Left(TaxPeriodNotEndedError)
      case None =>
        Right(None)

object ObligationsResponse:
  given format: OFormat[ObligationsResponse] = Json.format[ObligationsResponse]

case class Obligation(identification: Option[Identification], obligationDetails: Seq[ObligationDetail])
object Obligation:
  given format: OFormat[Obligation] = Json.format[Obligation]

case class Identification(
    referenceNumber: String,
    referenceType: String
)
object Identification:
  given format: OFormat[Identification] = Json.format[Identification]

case class ObligationDetail(
    inboundCorrespondenceFromDate: String,
    inboundCorrespondenceToDate: String,
    periodKey: String
)
object ObligationDetail:
  given format: OFormat[ObligationDetail] = Json.format[ObligationDetail]

case class ObligationPeriod(startDate: String, endDate: String)
