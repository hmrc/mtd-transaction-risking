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
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class ReportRequestSpec extends UnitSpec:

  private val correlationId = "2dd537bc-4244-4ebf-bac9-96321be13cdc"
  private val vrn = "123456789"
  private val periodKey = "#001"
  private val startDate = "2026-01-01"
  private val endDate = "2026-03-31"
  private val agentReferenceNumber = "LARN0085901"
  private val riskScore = BigDecimal("4.7")
  private val riskReasons = Seq("VRN 123456789 is 3.7 hops away from something risky.")

  private val vendorBody: JsValue = Json.parse(
    """
      |{
      |  "periodKey": "#001",
      |  "vatDueSales": 100.00,
      |  "vatDueAcquisitions": 100.00,
      |  "totalVatDue": 200.00,
      |  "vatReclaimedCurrPeriod": 100.00,
      |  "netVatDue": 100.00,
      |  "totalValueSalesExVAT": 500,
      |  "totalValuePurchasesExVAT": 400,
      |  "totalValueGoodsSuppliedExVAT": 300,
      |  "totalAcquisitionsExVAT": 200
      |}
      |""".stripMargin
  )

  private val requestHeaders: Seq[(String, String)] = Seq(
    "Gov-Client-Connection-Method" -> "DESKTOP_APP_VIA_SERVER",
    "gov-vendor-version" -> "my-desktop-app=2.2.2",
    "Authorization" -> "Bearer abc123",
    "Accept" -> "application/vnd.hmrc.1.0+json"
  )

  private def build(vendorBody: JsValue = vendorBody,
                    agentReferenceNumber: Option[String] = None,
                    requestHeaders: Seq[(String, String)] = requestHeaders,
                    riskReasons: Seq[String] = riskReasons): Option[ReportRequest] =
    ReportRequest.from(
      correlationId = correlationId,
      vrn = vrn,
      vendorBody = vendorBody,
      agentReferenceNumber = agentReferenceNumber,
      periodKey = periodKey,
      startDate = startDate,
      endDate = endDate,
      fraudRiskReportScore = riskScore,
      fraudRiskReportReasons = riskReasons,
      requestHeaders = requestHeaders
    )

  private def gridRows(json: JsValue, name: String): Seq[Seq[String]] =
    val grid = inputValue(json, name).as[Seq[JsObject]]
    (grid(1) \ "data").as[Seq[Seq[String]]]

  private def inputValue(json: JsValue, name: String): JsValue =
    (json \ "inputs")
      .as[Seq[JsObject]]
      .find(input => (input \ "name").as[String] == name)
      .map(input => (input \ "value").get)
      .getOrElse(fail(s"input $name was not written"))

  "from" when:

    "the vendor body is complete" should:

      "carry the correlation id, vrn, obligation dates and insights values through" in:
        val request = build().value

        request.fixedId shouldBe correlationId
        request.vrn shouldBe vrn
        request.periodKey shouldBe periodKey
        request.startDate shouldBe startDate
        request.endDate shouldBe endDate
        request.fraudRiskReportScore shouldBe riskScore
        request.fraudRiskReportReasons shouldBe riskReasons

      "keep the vendor's VAT field names and values" in:
        val request = build().value

        request.vatDueSales shouldBe BigDecimal("100.00")
        request.vatDueAcquisitions shouldBe BigDecimal("100.00")
        request.totalVatDue shouldBe BigDecimal("200.00")
        request.vatReclaimedCurrPeriod shouldBe BigDecimal("100.00")
        request.netVatDue shouldBe BigDecimal("100.00")
        request.totalValueSalesExVAT shouldBe BigDecimal(500)
        request.totalValuePurchasesExVAT shouldBe BigDecimal(400)
        request.totalValueGoodsSuppliedExVAT shouldBe BigDecimal(300)
        request.totalAcquisitionsExVAT shouldBe BigDecimal(200)

    "an agent reference number is supplied" should:
      "set the customer type to agent" in:
        val request = build(agentReferenceNumber = Some(agentReferenceNumber)).value

        request.customerType shouldBe "A"
        request.agentReferenceNumber shouldBe Some(agentReferenceNumber)

    "no agent reference number is supplied" should:
      "set the customer type to taxpayer" in:
        val request = build().value

        request.customerType shouldBe "T"
        request.agentReferenceNumber shouldBe None

    "the request carries fraud prevention headers" should:

      "keep only the gov-client and gov-vendor headers" in:
        build().value.fraudPreventionHeaders.map(_.key) should contain theSameElementsAs
          Seq("Gov-Client-Connection-Method", "Gov-Vendor-Version")

      "write known headers in correct casing whatever the vendor sent" in:
        build().value.fraudPreventionHeaders should contain(FraudPreventionHeader("Gov-Vendor-Version", "my-desktop-app=2.2.2"))

      "pass unknown gov headers through as received" in:
        val headers = build(requestHeaders = Seq("Gov-Client-New-Thing" -> "x")).value.fraudPreventionHeaders

        headers shouldBe Seq(FraudPreventionHeader("Gov-Client-New-Thing", "x"))

      "not change header values" in:
        build(requestHeaders = Seq("gov-client-timezone" -> "UTC+00:00")).value.fraudPreventionHeaders shouldBe
          Seq(FraudPreventionHeader("Gov-Client-Timezone", "UTC+00:00"))

      "return no headers when none are fraud prevention headers" in:
        build(requestHeaders = Seq("Accept" -> "application/json")).value.fraudPreventionHeaders shouldBe empty

    "a mandatory VAT figure is missing" should:
      "return None for any of the mandatory figures" in:
        val mandatoryFields = Seq(
          "vatDueSales",
          "vatDueAcquisitions",
          "totalVatDue",
          "vatReclaimedCurrPeriod",
          "netVatDue",
          "totalValueSalesExVAT",
          "totalValuePurchasesExVAT",
          "totalValueGoodsSuppliedExVAT",
          "totalAcquisitionsExVAT"
        )

        mandatoryFields.foreach { field =>
          withClue(s"when $field is missing: ")(build(vendorBody = vendorBody.as[JsObject] - field) shouldBe None)
        }

    "a VAT figure is not numeric" should:
      "return None" in:
        val nonNumeric = vendorBody.as[JsObject] + ("vatDueSales" -> Json.toJson("not-a-number"))

        build(vendorBody = nonNumeric) shouldBe None

  "ReportRequest" when:

    "written to JSON" should:

      "wrap every field in the SAS inputs envelope" in:
        val json = Json.toJson(build().value)

        (json \ "inputs").as[Seq[JsObject]].map(input => (input \ "name").as[String]) should contain theSameElementsAs Seq(
          "fixedId",
          "vrn",
          "periodKey",
          "startDate",
          "endDate",
          "customerType",
          "agentReferenceNumber",
          "vatDueSales",
          "vatDueAcquisitions",
          "totalVatDue",
          "vatReclaimedCurrPeriod",
          "netVatDue",
          "totalValueSalesExVAT",
          "totalValuePurchasesExVAT",
          "totalValueGoodsSuppliedExVAT",
          "totalAcquisitionsExVAT",
          "fraudRiskReportScore",
          "fraudPreventionHeaders",
          "fraudRiskReportReasons"
        )

      "write identifiers as strings and amounts as numbers" in:
        val json = Json.toJson(build(agentReferenceNumber = Some(agentReferenceNumber)).value)

        inputValue(json, "fixedId").as[String] shouldBe correlationId
        inputValue(json, "vrn").as[String] shouldBe vrn
        inputValue(json, "customerType").as[String] shouldBe "A"
        inputValue(json, "agentReferenceNumber").as[String] shouldBe agentReferenceNumber
        inputValue(json, "totalVatDue").as[BigDecimal] shouldBe BigDecimal("200.00")
        inputValue(json, "netVatDue").as[BigDecimal] shouldBe BigDecimal("100.00")
        inputValue(json, "totalAcquisitionsExVAT").as[BigDecimal] shouldBe BigDecimal(200)
        inputValue(json, "fraudRiskReportScore").as[BigDecimal] shouldBe riskScore

      "write a null agent reference number for a taxpayer, keeping the input present" in:
        inputValue(Json.toJson(build().value), "agentReferenceNumber") shouldBe JsNull

      "write the fraud prevention headers as a key/value data grid" in:
        val json = Json.toJson(build().value)

        inputValue(json, "fraudPreventionHeaders").as[Seq[JsObject]].head shouldBe
          Json.obj("metadata" -> Json.arr(Json.obj("key" -> "string"), Json.obj("value" -> "string")))

        gridRows(json, "fraudPreventionHeaders") should contain(Seq("Gov-Vendor-Version", "my-desktop-app=2.2.2"))

      "write the risk reasons as a Reason data grid" in:
        val json = Json.toJson(build().value)

        inputValue(json, "fraudRiskReportReasons").as[Seq[JsObject]].head shouldBe
          Json.obj("metadata" -> Json.arr(Json.obj("Reason" -> "string")))

        gridRows(json, "fraudRiskReportReasons") shouldBe riskReasons.map(Seq(_))

      "keep the grid metadata with no rows when there are no headers or reasons" in:
        val json = Json.toJson(build(requestHeaders = Nil, riskReasons = Nil).value)

        gridRows(json, "fraudPreventionHeaders") shouldBe empty
        gridRows(json, "fraudRiskReportReasons") shouldBe empty
