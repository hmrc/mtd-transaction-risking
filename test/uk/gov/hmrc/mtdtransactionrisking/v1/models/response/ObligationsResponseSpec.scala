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

  private val obligation1: Obligation =
    Obligation(
      status = "O",
      start = "2019-01-01",
      end = "2019-01-31",
      due = "2019-03-07",
      periodKey = "19AA"
    )

  private val obligation2: Obligation =
    Obligation(
      status = "O",
      start = "2019-02-01",
      end = "2019-02-28",
      due = "2019-04-07",
      periodKey = "19AB"
    )

  private val obligationsResponseModel: ObligationsResponse =
    ObligationsResponse(Seq(obligation1, obligation2))

  private val obligationsResponseJson: JsValue = Json.parse(
    """|{
       |  "obligations": [
       |    {
       |      "status": "O",
       |      "start": "2019-01-01",
       |      "end": "2019-01-31",
       |      "due": "2019-03-07",
       |      "periodKey": "19AA"
       |    },
       |    {
       |      "status": "O",
       |      "start": "2019-02-01",
       |      "end": "2019-02-28",
       |      "due": "2019-04-07",
       |      "periodKey": "19AB"
       |    }
       |  ]
       |}""".stripMargin
  )

  private val obligationsWithExtraFieldsJson: JsValue = Json.parse(
    """|{
       |  "obligations": [
       |    {
       |      "status": "F",
       |      "start": "2019-01-01",
       |      "end": "2019-01-31",
       |      "due": "2019-03-07",
       |      "periodKey": "19AA",
       |      "received": "2019-03-06"
       |    },
       |    {
       |      "status": "O",
       |      "start": "2019-02-01",
       |      "end": "2019-02-28",
       |      "due": "2019-04-07",
       |      "periodKey": "19AB"
       |    }
       |  ]
       |}""".stripMargin
  )

  "ObligationsResponse" should {
    "serialize to JSON correctly" in {
      Json.toJson(obligationsResponseModel) shouldBe obligationsResponseJson
    }

    "deserialize from JSON correctly" in {
      obligationsResponseJson.as[ObligationsResponse] shouldBe obligationsResponseModel
    }

    "ignore unknown fields in obligation items when deserializing" in {
      obligationsWithExtraFieldsJson.as[ObligationsResponse] shouldBe
        ObligationsResponse(
          Seq(
            Obligation("F", "2019-01-01", "2019-01-31", "2019-03-07", "19AA"),
            Obligation("O", "2019-02-01", "2019-02-28", "2019-04-07", "19AB")
          )
        )
    }
  }

  "Obligation" should {
    "serialize to JSON correctly" in {
      Json.toJson(obligation1) shouldBe Json.parse(
        """|{
           |  "status": "O",
           |  "start": "2019-01-01",
           |  "end": "2019-01-31",
           |  "due": "2019-03-07",
           |  "periodKey": "19AA"
           |}""".stripMargin
      )
    }

    "deserialize from JSON correctly" in {
      Json.parse(
        """|{
           |  "status": "O",
           |  "start": "2019-01-01",
           |  "end": "2019-01-31",
           |  "due": "2019-03-07",
           |  "periodKey": "19AA"
           |}""".stripMargin
      ).as[Obligation] shouldBe obligation1
    }
  }
}