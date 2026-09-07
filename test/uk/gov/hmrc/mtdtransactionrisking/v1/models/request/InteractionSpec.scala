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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackLink, FeedbackMessage, FeedbackResponse, Obligation}

import java.time.Instant

class InteractionSpec extends UnitSpec:

  private val vrn = "123456789"
  private val now = Instant.parse("2026-08-13T09:00:00Z")

  private val obligation = Obligation(
    periodKey = "AB12",
    start = "2026-01-01",
    end = "2026-03-31",
    due = "2026-05-07",
    status = "O",
    received = None
  )

  private val vendorBody: JsValue = Json.parse(
    """
      |{
      |  "periodKey": "AB12",
      |  "vatDueSales": 100.00,
      |  "vatDueAcquisitions": 100.00,
      |  "totalVatDue": 200.00,
      |  "vatReclaimedCurrPeriod": 100.00,
      |  "netVatDue": 100.00,
      |  "totalValueSalesExVAT": 500,
      |  "totalValuePurchasesExVAT": 500,
      |  "totalValueGoodsSuppliedExVAT": 500,
      |  "totalAcquisitionsExVAT": 500
      |}
      |""".stripMargin
  )

  private def englishMessage(itemNumber: String) = FeedbackMessage(
    itemNumber = itemNumber,
    title = "VAT title",
    body = "VAT body",
    action = Some("VAT action"),
    links = Some(List(FeedbackLink("VAT guidance", "https://www.gov.uk/vat-returns"))),
    path = "/guidance"
  )

  private def welshMessage(itemNumber: String) = FeedbackMessage(
    itemNumber = itemNumber,
    title = "Teitl TAW",
    body = "Corff TAW",
    action = Some("Gweithred TAW"),
    links = Some(List(FeedbackLink("Canllawiau TAW", "https://www.gov.uk/ffurflenni-taw"))),
    path = "/guidance"
  )

  "Interaction.forGeneratedReport" when:

    "there is one matched pair of feedback messages" should:

      "set the service regime and event name" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("1")), List(welshMessage("1")), "rds-corr-id")

        val interaction = Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now)

        interaction.serviceRegime shouldBe "vat-assist"
        interaction.eventName shouldBe "generate-report"
        interaction.feedbackId shouldBe "report-1"
        interaction.eventTimestamp shouldBe now.toString

      "carry the vrn and obligation dates in the metadata" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("1")), List(welshMessage("1")), "rds-corr-id")

        val metadata = Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).metadata.head

        metadata.vrn shouldBe vrn
        metadata.start shouldBe Some(obligation.start)
        metadata.end shouldBe Some(obligation.end)

      "keep only the VAT return fields in additionalProperties" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("1")), List(welshMessage("1")), "rds-corr-id")

        val additionalProperties = Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).metadata.head.additionalProperties

        (additionalProperties \ "vatDueSales").asOpt[BigDecimal] shouldBe Some(BigDecimal("100.00"))
        (additionalProperties \ "totalVatDue").asOpt[BigDecimal] shouldBe Some(BigDecimal("200.00"))

      "nest the paired english and welsh actions under one message with an integer item number" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("1")), List(welshMessage("1")), "rds-corr-id")
        val messages = Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).payload.messages.get

        messages should have size 1

        val message = messages.head

        message.englishActions.itemNumber shouldBe 1
        message.englishActions.title shouldBe "VAT title"
        message.welshActions.itemNumber shouldBe 1
        message.welshActions.title shouldBe "Teitl TAW"

      "map links to linkTitle and linkUrl" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("1")), List(welshMessage("1")), "rds-corr-id")
        val message = Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).payload.messages.get.head

        message.englishActions.links shouldBe List(InteractionLink("VAT guidance", "https://www.gov.uk/vat-returns"))

      "default a missing action to an empty string" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("1").copy(action = None)), List(welshMessage("1")), "rds-corr-id")
        val message = Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).payload.messages.get.head

        message.englishActions.action shouldBe ""

    "an english message has no matching welsh item number" should:
      "drop it forGeneratedReport the payload" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("1")), List(welshMessage("2")), "rds-corr-id")

        Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).payload.messages.get shouldBe empty

    "an item number is not numeric" should:
      "drop that message" in:
        val feedback = FeedbackResponse("report-1", List(englishMessage("not-a-number")), List(welshMessage("not-a-number")), "rds-corr-id")

        Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).payload.messages.get shouldBe empty

    "there are several matched pairs" should:
      "include every matched message" in:
        val feedback = FeedbackResponse(
          "report-1",
          List(englishMessage("1"), englishMessage("2")),
          List(welshMessage("1"), welshMessage("2")),
          "rds-corr-id"
        )

        Interaction.forGeneratedReport(feedback, obligation, vrn, vendorBody, now).payload.messages.get should have size 2

  "Interaction.forAcknowledgement" when:

    "building the interaction" should:

      "set the service regime and event name" in:
        val request = AcknowledgeRequest(
          vrn = vrn,
          reportId = "report-1",
          correlationId = "9EEB55EF4FA9A24954BC982DF1D59B3D02BC097F6B1377B8B335C7583D92B959",
          presentedDateTime = "2026-08-13T09:00:00Z"
        )

        val interaction = Interaction.forAcknowledgement(request, now)

        interaction.serviceRegime shouldBe "vat-assist"
        interaction.eventName shouldBe "acknowledge-report"
        interaction.feedbackId shouldBe "report-1"
        interaction.eventTimestamp shouldBe now.toString

      "carry the vrn but no obligation dates in the metadata" in:
        val request = AcknowledgeRequest(vrn, "report-1", "9EEB55EF4FA9A24954BC982DF1D59B3D02BC097F6B1377B8B335C7583D92B959", "2026-08-13T09:00:00Z")

        val metadata = Interaction.forAcknowledgement(request, now).metadata.head

        metadata.vrn shouldBe vrn
        metadata.start shouldBe None
        metadata.end shouldBe None

      "carry presentedDateTime in additionalProperties" in:
        val request = AcknowledgeRequest(vrn, "report-1", "9EEB55EF4FA9A24954BC982DF1D59B3D02BC097F6B1377B8B335C7583D92B959", "2026-08-13T09:00:00Z")

        val additionalProperties = Interaction.forAcknowledgement(request, now).metadata.head.additionalProperties

        (additionalProperties \ "presentedDateTime").asOpt[String] shouldBe Some("2026-08-13T09:00:00Z")

      "have no payload messages" in:
        val request = AcknowledgeRequest(vrn, "report-1", "9EEB55EF4FA9A24954BC982DF1D59B3D02BC097F6B1377B8B335C7583D92B959", "2026-08-13T09:00:00Z")

        Interaction.forAcknowledgement(request, now).payload.messages shouldBe None

      "carry the report id as feedbackId and the payload's reportId" in:
        val request = AcknowledgeRequest(vrn, "report-1", "9EEB55EF4FA9A24954BC982DF1D59B3D02BC097F6B1377B8B335C7583D92B959", "2026-08-13T09:00:00Z")

        val interaction = Interaction.forAcknowledgement(request, now)

        interaction.feedbackId shouldBe "report-1"
        interaction.payload.reportId shouldBe "report-1"
