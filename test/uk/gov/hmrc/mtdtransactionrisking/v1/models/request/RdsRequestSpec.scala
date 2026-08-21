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

import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class RdsRequestSpec extends UnitSpec:

  private val correlationId        = "2dd537bc-4244-4ebf-bac9-96321be13cdc"
  private val periodKey            = "#001"
  private val startDate            = "2026-01-01"
  private val endDate              = "2026-03-31"
  private val agentReferenceNumber = "LARN0085901"
  private val riskScore            = 4.7
  private val riskReasons          = Seq("VRN 123456789 is 3.7 hops away from something risky.")

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
    "Gov-Vendor-Version"           -> "my-desktop-app=2.2.2",
    "Authorization"                -> "Bearer abc123",
    "Accept"                       -> "application/vnd.hmrc.1.0+json"
  )

  private def build(vendorBody: JsValue = vendorBody,
                    agentReferenceNumber: Option[String] = None,
                    requestHeaders: Seq[(String, String)] = requestHeaders): Option[RdsRequest] =
    RdsRequest.from(
      correlationId          = correlationId,
      vendorBody             = vendorBody,
      agentReferenceNumber   = agentReferenceNumber,
      periodKey              = periodKey,
      startDate              = startDate,
      endDate                = endDate,
      fraudRiskReportScore   = riskScore,
      fraudRiskReportReasons = riskReasons,
      requestHeaders         = requestHeaders
    )

  "from" when:

    "the vendor body is complete" should:

      "carry the correlation id, obligation dates and insights values through" in:
        val request = build().value

        request.fixedId shouldBe correlationId
        request.periodKey shouldBe periodKey
        request.startDate shouldBe startDate
        request.endDate shouldBe endDate
        request.fraudRiskReportScore shouldBe riskScore
        request.fraudRiskReportReasons shouldBe riskReasons

      "map the VAT figures to the downstream's field names" in:
        val request = build().value

        request.vatDueSales shouldBe BigDecimal("100.00")
        request.vatDueAcquisitions shouldBe BigDecimal("100.00")
        request.vatDueTotal shouldBe BigDecimal("200.00")
        request.vatReclaimedCurrPeriod shouldBe BigDecimal("100.00")
        request.vatDueNet shouldBe BigDecimal("100.00")
        request.totalValueSalesExVAT shouldBe BigDecimal(500)
        request.totalValuePurchasesExVAT shouldBe BigDecimal(400)
        request.totalValueGoodsSuppliedExVAT shouldBe BigDecimal(300)
        request.totalAllAcquisitionsExVAT shouldBe BigDecimal(200)

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
        val headers = build().value.fraudPreventionHeaders

        headers.map(_.key) should contain theSameElementsAs Seq("gov-client-connection-method", "gov-vendor-version")

      "lower-case the header keys but not their values" in:
        val headers = build().value.fraudPreventionHeaders

        headers should contain(FraudPreventionHeader("gov-vendor-version", "my-desktop-app=2.2.2"))

      "return no headers when none are fraud prevention headers" in:
        build(requestHeaders = Seq("Accept" -> "application/json")).value.fraudPreventionHeaders shouldBe empty

    "a mandatory VAT figure is missing" should:

      "return None" in:
        build(vendorBody = vendorBody.as[JsObject] - "vatDueSales") shouldBe None

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

  "RdsRequest" when:
    "written to JSON" should:

      "use the downstream's field names" in:
        val json = Json.toJson(build(agentReferenceNumber = Some(agentReferenceNumber)).value)

        (json \ "fixedId").as[String] shouldBe correlationId
        (json \ "customerType").as[String] shouldBe "A"
        (json \ "agentReferenceNumber").as[String] shouldBe agentReferenceNumber
        (json \ "vatDueTotal").as[BigDecimal] shouldBe BigDecimal("200.00")
        (json \ "vatDueNet").as[BigDecimal] shouldBe BigDecimal("100.00")
        (json \ "totalAllAcquisitionsExVAT").as[BigDecimal] shouldBe BigDecimal(200)

      "write the fraud prevention headers as key/value pairs" in:
        val json = Json.toJson(build().value)

        (json \ "fraudPreventionHeaders").as[Seq[JsObject]] should contain(
          Json.obj("key" -> "gov-vendor-version", "value" -> "my-desktop-app=2.2.2"))

      "omit the agent reference number when absent" in:
        val json = Json.toJson(build().value)

        (json \ "agentReferenceNumber").isDefined shouldBe false