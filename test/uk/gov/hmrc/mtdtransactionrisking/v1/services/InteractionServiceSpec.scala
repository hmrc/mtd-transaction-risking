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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.mocks.services.MockRsdRetryService
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.Interaction
import uk.gov.hmrc.mtdtransactionrisking.v1.models.response.{FeedbackLink, FeedbackMessage, FeedbackResponse, Obligation}

import java.time.{Clock, Instant, ZoneOffset}

class InteractionServiceSpec
  extends UnitSpec
    with MockRsdRetryService:

  implicit val correlationId: CorrelationId =
    CorrelationId("test-correlation-id")

  private val vrn =
    "123456789"

  private val now =
    Instant.parse("2026-08-13T09:00:00Z")

  private val clock =
    Clock.fixed(now, ZoneOffset.UTC)

  private val obligation =
    Obligation(
      periodKey = "AB12",
      start = "2026-01-01",
      end = "2026-03-31",
      due = "2026-05-07",
      status = "O",
      received = None
    )

  private val vendorBody: JsValue =
    Json.parse(
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
        |  "totalAcquisitionsExVAT": 500
        |}
        |""".stripMargin
    )

  private val englishMessage =
    FeedbackMessage(
      itemNumber = "1",
      title = "VAT title",
      body = "VAT body",
      action = Some("VAT action"),
      links = Some(
        List(
          FeedbackLink(
            "VAT",
            "https://www.gov.uk/vat"
          )
        )
      ),
      path = Some("/guidance")
    )

  private val feedback =
    FeedbackResponse(
      reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      englishFeedback = List(englishMessage),
      welshFeedback = List(englishMessage),
      correlationId = None
    )

  private val expectedInteraction: Interaction =
    Interaction.forGeneratedReport(
      feedback = feedback,
      obligation = obligation,
      vrn = vrn,
      vendorBody = vendorBody,
      occurredAt = now
    )

  private trait Test:
    val service =
      new InteractionService(
        rsdRetryService = mockRsdRetryService,
        clock = clock
      )

  "InteractionService.store" should :

    "build the generated-report interaction and submit it with the TxR-generated correlation ID" in new Test:
      MockRsdRetryService
        .submitInitial(
          interaction = expectedInteraction,
          correlationId = correlationId
        )
        .returns(())

      service.store(
        feedback = feedback,
        obligation = obligation,
        vrn = vrn,
        vendorBody = vendorBody
      )

    "not depend on the nullable downstream response-data correlationId" in new Test:
      MockRsdRetryService
        .submitInitial(
          interaction = expectedInteraction,
          correlationId = correlationId
        )
        .returns(())

      noException shouldBe thrownBy {
        service.store(
          feedback = feedback,
          obligation = obligation,
          vrn = vrn,
          vendorBody = vendorBody
        )
      }
