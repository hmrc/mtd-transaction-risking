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

import play.api.libs.json.*

case class FraudPreventionHeader(key: String, value: String)

case class ReportRequest(
    fixedId: String,
    vrn: String,
    periodKey: String,
    startDate: String,
    endDate: String,
    customerType: String,
    agentReferenceNumber: Option[String],
    vatDueSales: BigDecimal,
    vatDueAcquisitions: BigDecimal,
    totalVatDue: BigDecimal,
    vatReclaimedCurrPeriod: BigDecimal,
    netVatDue: BigDecimal,
    totalValueSalesExVAT: BigDecimal,
    totalValuePurchasesExVAT: BigDecimal,
    totalValueGoodsSuppliedExVAT: BigDecimal,
    totalAcquisitionsExVAT: BigDecimal,
    fraudRiskReportScore: BigDecimal,
    fraudPreventionHeaders: Seq[FraudPreventionHeader],
    fraudRiskReportReasons: Seq[String]
)

object ReportRequest:

  private val agent = "A"
  private val taxPayer = "T"

  private val govHeaderPrefixes = Seq("gov-client-", "gov-vendor-")

  // RDS matches on exact header names, so known headers are sent in canonical casing unknown Gov-* headers pass through as received.
  private val headerNames: Map[String, String] = Seq(
    "Gov-Client-Connection-Method",
    "Gov-Client-Device-ID",
    "Gov-Client-Local-IPs",
    "Gov-Client-Local-IPs-Timestamp",
    "Gov-Client-MAC-Addresses",
    "Gov-Client-Multi-Factor",
    "Gov-Client-Public-IP",
    "Gov-Client-Public-IP-Timestamp",
    "Gov-Client-Public-Port",
    "Gov-Client-Screens",
    "Gov-Client-Timezone",
    "Gov-Client-User-Agent",
    "Gov-Client-User-IDs",
    "Gov-Client-Window-Size",
    "Gov-Client-Browser-JS-User-Agent",
    "Gov-Client-Browser-Do-Not-Track",
    "Gov-Vendor-Forwarded",
    "Gov-Vendor-License-IDs",
    "Gov-Vendor-Product-Name",
    "Gov-Vendor-Public-IP",
    "Gov-Vendor-Version"
  ).map(name => name.toLowerCase -> name).toMap

  given writes: OWrites[ReportRequest] = OWrites { request =>

    def input(name: String, value: JsValue): JsObject = Json.obj("name" -> name, "value" -> value)

    Json.obj(
      "inputs" -> Json.arr(
        input("fixedId", JsString(request.fixedId)),
        input("vrn", JsString(request.vrn)),
        input("periodKey", JsString(request.periodKey)),
        input("startDate", JsString(request.startDate)),
        input("endDate", JsString(request.endDate)),
        input("customerType", JsString(request.customerType)),
        // Every variable must be present; null when not an agent
        input("agentReferenceNumber", request.agentReferenceNumber.fold[JsValue](JsNull)(JsString(_))),
        input("vatDueSales", JsNumber(request.vatDueSales)),
        input("vatDueAcquisitions", JsNumber(request.vatDueAcquisitions)),
        input("totalVatDue", JsNumber(request.totalVatDue)),
        input("vatReclaimedCurrPeriod", JsNumber(request.vatReclaimedCurrPeriod)),
        input("netVatDue", JsNumber(request.netVatDue)),
        input("totalValueSalesExVAT", JsNumber(request.totalValueSalesExVAT)),
        input("totalValuePurchasesExVAT", JsNumber(request.totalValuePurchasesExVAT)),
        input("totalValueGoodsSuppliedExVAT", JsNumber(request.totalValueGoodsSuppliedExVAT)),
        input("totalAcquisitionsExVAT", JsNumber(request.totalAcquisitionsExVAT)),
        input("fraudRiskReportScore", JsNumber(request.fraudRiskReportScore)),
        input("fraudPreventionHeaders", grid(Seq("key", "value"), request.fraudPreventionHeaders.map(header => Seq(header.key, header.value)))),
        input("fraudRiskReportReasons", grid(Seq("Reason"), request.fraudRiskReportReasons.map(Seq(_))))
      )
    )
  }

  // SAS data grid with no rows data is an empty array.
  private def grid(columns: Seq[String], rows: Seq[Seq[String]]): JsValue =
    Json.arr(
      Json.obj("metadata" -> columns.map(column => Json.obj(column -> "string"))),
      Json.obj("data" -> rows)
    )

  def from(
      correlationId: String,
      vrn: String,
      vendorBody: JsValue,
      agentReferenceNumber: Option[String],
      periodKey: String,
      startDate: String,
      endDate: String,
      fraudRiskReportScore: BigDecimal,
      fraudRiskReportReasons: Seq[String],
      requestHeaders: Seq[(String, String)]
  ): Option[ReportRequest] =

    def amount(field: String): Option[BigDecimal] = (vendorBody \ field).asOpt[BigDecimal]

    for
      vatDueSales <- amount("vatDueSales")
      vatDueAcquisitions <- amount("vatDueAcquisitions")
      totalVatDue <- amount("totalVatDue")
      vatReclaimedCurrPeriod <- amount("vatReclaimedCurrPeriod")
      netVatDue <- amount("netVatDue")
      totalValueSalesExVAT <- amount("totalValueSalesExVAT")
      totalValuePurchasesExVAT <- amount("totalValuePurchasesExVAT")
      totalValueGoodsSuppliedExVAT <- amount("totalValueGoodsSuppliedExVAT")
      totalAcquisitionsExVAT <- amount("totalAcquisitionsExVAT")
    yield ReportRequest(
      fixedId = correlationId,
      vrn = vrn,
      periodKey = periodKey,
      startDate = startDate,
      endDate = endDate,
      customerType = if agentReferenceNumber.isDefined then agent else taxPayer,
      agentReferenceNumber = agentReferenceNumber,
      vatDueSales = vatDueSales,
      vatDueAcquisitions = vatDueAcquisitions,
      totalVatDue = totalVatDue,
      vatReclaimedCurrPeriod = vatReclaimedCurrPeriod,
      netVatDue = netVatDue,
      totalValueSalesExVAT = totalValueSalesExVAT,
      totalValuePurchasesExVAT = totalValuePurchasesExVAT,
      totalValueGoodsSuppliedExVAT = totalValueGoodsSuppliedExVAT,
      totalAcquisitionsExVAT = totalAcquisitionsExVAT,
      fraudRiskReportScore = fraudRiskReportScore,
      fraudPreventionHeaders = fraudPreventionHeaders(requestHeaders),
      fraudRiskReportReasons = fraudRiskReportReasons
    )

  private def fraudPreventionHeaders(requestHeaders: Seq[(String, String)]): Seq[FraudPreventionHeader] =
    requestHeaders.collect {
      case (name, value) if govHeaderPrefixes.exists(name.toLowerCase.startsWith) =>
        FraudPreventionHeader(headerNames.getOrElse(name.toLowerCase, name), value)
    }
