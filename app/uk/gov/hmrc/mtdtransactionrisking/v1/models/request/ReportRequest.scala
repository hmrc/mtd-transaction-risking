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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.request

import play.api.libs.json.{JsValue, Json, OWrites}

case class FraudPreventionHeader(key: String, value: String)

object FraudPreventionHeader:
  given writes: OWrites[FraudPreventionHeader] = Json.writes[FraudPreventionHeader]

case class ReportRequest(
    fixedId: String,
    periodKey: String,
    startDate: String,
    endDate: String,
    customerType: String,
    agentReferenceNumber: Option[String],
    fraudRiskReportScore: Double,
    fraudRiskReportReasons: Seq[String],
    fraudPreventionHeaders: Seq[FraudPreventionHeader],
    vatDueSales: BigDecimal,
    vatDueAcquisitions: BigDecimal,
    vatDueTotal: BigDecimal,
    vatReclaimedCurrPeriod: BigDecimal,
    vatDueNet: BigDecimal,
    totalValueSalesExVAT: BigDecimal,
    totalValuePurchasesExVAT: BigDecimal,
    totalValueGoodsSuppliedExVAT: BigDecimal,
    totalAllAcquisitionsExVAT: BigDecimal
)
object ReportRequest:

  private val agent = "A"
  private val taxPayer = "T"
  private val govHeaderPrefixes = Seq("gov-client-", "gov-vendor-")

  given writes: OWrites[ReportRequest] = Json.writes[ReportRequest]

  /** Assembles the payload from the vendor's return, the matched obligation, and the auth ARN.
    *
    * The vendor body has already passed validation upstream, so every mandatory numeric field is present
    */
  def from(
      correlationId: String,
      vendorBody: JsValue,
      agentReferenceNumber: Option[String],
      periodKey: String,
      startDate: String,
      endDate: String,
      fraudRiskReportScore: Double,
      fraudRiskReportReasons: Seq[String],
      requestHeaders: Seq[(String, String)]
  ): Option[ReportRequest] =

    def amount(field: String): Option[BigDecimal] = (vendorBody \ field).asOpt[BigDecimal]

    for
      vatDueSales <- amount("vatDueSales")
      vatDueAcquisitions <- amount("vatDueAcquisitions")
      vatDueTotal <- amount("totalVatDue")
      vatReclaimedCurrPeriod <- amount("vatReclaimedCurrPeriod")
      vatDueNet <- amount("netVatDue")
      totalValueSalesExVAT <- amount("totalValueSalesExVAT")
      totalValuePurchasesExVAT <- amount("totalValuePurchasesExVAT")
      totalValueGoodsSuppliedExVAT <- amount("totalValueGoodsSuppliedExVAT")
      totalAllAcquisitionsExVAT <- amount("totalAcquisitionsExVAT")
    yield ReportRequest(
      fixedId = correlationId,
      periodKey = periodKey,
      startDate = startDate,
      endDate = endDate,
      customerType = if agentReferenceNumber.isDefined then agent else taxPayer,
      agentReferenceNumber = agentReferenceNumber,
      fraudRiskReportScore = fraudRiskReportScore,
      fraudRiskReportReasons = fraudRiskReportReasons,
      fraudPreventionHeaders = requestHeaders.collect {
        case (key, value) if govHeaderPrefixes.exists(key.toLowerCase.startsWith) =>
          FraudPreventionHeader(key.toLowerCase, value)
      },
      vatDueSales = vatDueSales,
      vatDueAcquisitions = vatDueAcquisitions,
      vatDueTotal = vatDueTotal,
      vatReclaimedCurrPeriod = vatReclaimedCurrPeriod,
      vatDueNet = vatDueNet,
      totalValueSalesExVAT = totalValueSalesExVAT,
      totalValuePurchasesExVAT = totalValuePurchasesExVAT,
      totalValueGoodsSuppliedExVAT = totalValueGoodsSuppliedExVAT,
      totalAllAcquisitionsExVAT = totalAllAcquisitionsExVAT
    )
