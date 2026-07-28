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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

class ObligationsResponseSpec extends AnyWordSpec with Matchers {


  val obligationDetailModel: ObligationDetail = ObligationDetail(
    inboundCorrespondenceFromDate = "2026-01-01",
    inboundCorrespondenceToDate = "2026-03-31",
    periodKey = "18A"
  )

  val obligationModel: Obligation = Obligation(
    obligationDetails = Seq(obligationDetailModel)
  )

  val obligationsResponseModel: ObligationsResponse =
    ObligationsResponse(
      obligations = Seq(
        Obligation(
          obligationDetails = Seq(
            ObligationDetail(
              inboundCorrespondenceFromDate = "2026-01-01",
              inboundCorrespondenceToDate = "2026-03-31",
              periodKey = "18A"
            )
          )
        )
      )
    )

  // Matches ObligationsStub payload shape (extra fields included)
  val stubLikeObligationsResponseJson: JsValue = Json.parse(
    """|{
       |  "obligations": [
       |    {
       |      "identification": {
       |        "referenceNumber": "AB123",
       |        "referenceType": "MTDBIS",
       |        "incomeSourceType": "ITSA"
       |      },
       |      "obligationDetails": [
       |        {
       |          "status": "O",
       |          "inboundCorrespondenceFromDate": "2026-01-01",
       |          "inboundCorrespondenceToDate": "2026-03-31",
       |          "inboundCorrespondenceDueDate": "2026-03-31",
       |          "inboundCorrespondenceDateReceived": "2026-01-01",
       |          "periodKey": "18A"
       |        }
       |      ]
       |    }
       |  ]
       |}""".stripMargin
  )


  val obligationDetailJson: JsValue = Json.parse(
    """|{
       |  "inboundCorrespondenceFromDate": "2026-01-01",
       |  "inboundCorrespondenceToDate": "2026-03-31",
       |  "periodKey": "18A"
       |}""".stripMargin
  )

  val obligationJson: JsValue = Json.parse(
    s"""|{
        |  "obligationDetails": [$obligationDetailJson]
        |}""".stripMargin
  )

  val obligationsResponseJson: JsValue = Json.parse(
    s"""|{
        |  "obligations": [$obligationJson]
        |}""".stripMargin
  )


  "ObligationsResponse" should {
    "serialize to JSON correctly (Writes)" in {
      Json.toJson(obligationsResponseModel) shouldBe obligationsResponseJson
    }

    "deserialize from stub-shaped JSON correctly (Reads)" in {
      stubLikeObligationsResponseJson.as[ObligationsResponse] shouldBe obligationsResponseModel
    }

    "deserialize from JSON correctly (Reads)" in {
      obligationsResponseJson.as[ObligationsResponse] shouldBe obligationsResponseModel
    }
  }

  "Obligation" should {
    "serialize to JSON correctly" in {
      Json.toJson(obligationModel) shouldBe obligationJson
    }

    "deserialize from JSON correctly" in {
      obligationJson.as[Obligation] shouldBe obligationModel
    }
  }

  "ObligationDetail" should {
    "serialize to JSON correctly" in {
      Json.toJson(obligationDetailModel) shouldBe obligationDetailJson
    }

    "deserialize from JSON correctly" in {
      obligationDetailJson.as[ObligationDetail] shouldBe obligationDetailModel
    }
  }
}