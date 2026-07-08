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

package uk.gov.hmrc.mtdtransactionrisking.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json

object FeedbackStub:

  private val feedbackUrl = "/feedback"

  def successResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "reportId" -> "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
                "correlationId" -> "c75f40a6-a3df-4429-a697-471eeec46435",
                "englishFeedback" -> Json.arr(
                  Json.obj(
                    "itemNumber" -> "001",
                    "title" -> "VAT due on sales appears low",
                    "body" -> "The VAT due on sales figure appears lower than expected.",
                    "path" -> "/guidance/vat-returns"
                  )
                ),
                "welshFeedback" -> Json.arr(
                  Json.obj(
                    "itemNumber" -> "001",
                    "title" -> "Mae'r TAW ar werthiannau'n isel",
                    "body" -> "Mae'r ffigwr TAW ar werthiannau'n ymddangos yn is na'r disgwyl.",
                    "path" -> "/guidance/vat-returns"
                  )
                )
              ).toString
            )
        )
    )

  def noFeedbackResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "reportId" -> "f2fb30e5-4ab6-4a29-b3c1-c00000000002",
                "correlationId" -> "c75f40a6-a3df-4429-a697-471eeec46436",
                "englishFeedback" -> Json.arr(),
                "welshFeedback" -> Json.arr()
              ).toString
            )
        )
    )

  def vrnInvalidResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "code" -> "VRN_INVALID",
                "message" -> "The provided VRN is invalid"
              ).toString
            )
        )
    )

  def periodKeyInvalidResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "code" -> "INVALID_REQUEST",
                "message" -> "Invalid request",
                "errors" -> Json.arr(
                  Json.obj(
                    "code" -> "PERIOD_KEY_INVALID",
                    "message" -> "period key should be a 4 character string",
                    "path" -> "/periodKey"
                  )
                )
              ).toString
            )
        )
    )

  def taxPeriodNotEndedResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(403)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "code" -> "TAX_PERIOD_NOT_ENDED",
                "message" -> "The remote endpoint has indicated that the submission is for a tax period that has not ended"
              ).toString
            )
        )
    )

  def insolventTraderResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(403)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "code" -> "RULE_INSOLVENT_TRADER",
                "message" -> "The remote endpoint has indicated that the Trader is insolvent"
              ).toString
            )
        )
    )

  def serverErrorResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "code" -> "INTERNAL_SERVER_ERROR",
                "message" -> "An internal server error occurred"
              ).toString
            )
        )
    )

  def malformedSuccessResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj("unexpected" -> "shape").toString)
        )
    )

  def connectionFaultResponse(): StubMapping =
    stubFor(
      post(urlEqualTo(feedbackUrl))
        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
    )