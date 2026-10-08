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

import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class MasEnvelopeSpec extends UnitSpec:

  private val request = ReportRequest(
    fixedId = "2dd537bc-4244-4ebf-bac9-96321be13cdc",
    vrn = "123456789",
    periodKey = "AB12",
    startDate = "2026-01-01",
    endDate = "2026-03-31",
    customerType = "T",
    agentReferenceNumber = "",
    fraudRiskReportScore = 4.7,
    fraudRiskReportReasons = Seq("VRN 123456789 is 3.7 hops away from something risky."),
    fraudPreventionHeaders = Seq(FraudPreventionHeader("gov-client-timezone", "UTC+00:00")),
    vatDueSales = BigDecimal("100.00"),
    vatDueAcquisitions = BigDecimal("100.00"),
    totalVatDue = BigDecimal("200.00"),
    vatReclaimedCurrPeriod = BigDecimal("100.00"),
    netVatDue = BigDecimal("100.00"),
    totalValueSalesExVAT = BigDecimal(500),
    totalValuePurchasesExVAT = BigDecimal(400),
    totalValueGoodsSuppliedExVAT = BigDecimal(300),
    totalAcquisitionsExVAT = BigDecimal(200)
  )

  private val inputs: Seq[JsObject] = (MasEnvelope(request) \ "inputs").as[Seq[JsObject]]

  private def valueOf(name: String): Option[JsObject] =
    inputs.find(input => (input \ "name").as[String] == name)

  "MasEnvelope" should:

    "wrap every field as a name/value pair under inputs" in:
      inputs should have size 19

      inputs.foreach { input =>
        (input \ "name").asOpt[String] should not be empty
        (input \ "value").isDefined shouldBe true
      }

    "name the inputs as the module declares them" in:
      val names = inputs.map(input => (input \ "name").as[String])

      names should contain theSameElementsAs Seq(
        "fixedId",
        "vrn",
        "periodKey",
        "startDate",
        "endDate",
        "customerType",
        "agentReferenceNumber",
        "fraudRiskReportScore",
        "fraudRiskReportReasons",
        "fraudPreventionHeaders",
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

    "carry the field values through unchanged" in:
      (valueOf("vrn").value \ "value").as[String] shouldBe "123456789"
      (valueOf("totalVatDue").value \ "value").as[BigDecimal] shouldBe BigDecimal("200.00")

    "include the agent reference number even when it is empty" in:
      valueOf("agentReferenceNumber") should not be empty

    "keep collection fields as JSON arrays" in:
      (valueOf("fraudPreventionHeaders").value \ "value").as[JsArray].value should have size 1
      (valueOf("fraudRiskReportReasons").value \ "value").as[JsArray].value should have size 1