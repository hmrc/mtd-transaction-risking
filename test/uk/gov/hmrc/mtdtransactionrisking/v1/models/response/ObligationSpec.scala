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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class ObligationSpec extends UnitSpec:

  private val openObligation = Obligation(
    periodKey = "19AA",
    start     = "2019-01-01",
    end       = "2019-01-31",
    due       = "2019-03-07",
    status    = "O",
    received  = None
  )

  private val fulfilledObligation = openObligation.copy(status = "F", received = Some("2019-03-06"))

  private val openObligationJson: JsValue = Json.parse(
    """
      |{
      |  "periodKey": "19AA",
      |  "start": "2019-01-01",
      |  "end": "2019-01-31",
      |  "due": "2019-03-07",
      |  "status": "O"
      |}
      |""".stripMargin
  )

  private val fulfilledObligationJson: JsValue = Json.parse(
    """
      |{
      |  "periodKey": "19AA",
      |  "start": "2019-01-01",
      |  "end": "2019-01-31",
      |  "due": "2019-03-07",
      |  "status": "F",
      |  "received": "2019-03-06"
      |}
      |""".stripMargin
  )

  "Obligation" when:

    "written to JSON" should:
      "omit received when it is absent" in:
        Json.toJson(openObligation) shouldBe openObligationJson

      "include received when it is present" in:
        Json.toJson(fulfilledObligation) shouldBe fulfilledObligationJson

    "read from JSON" should:
      "deserialise an obligation with no received date" in:
        openObligationJson.as[Obligation] shouldBe openObligation

      "deserialise an obligation with a received date" in:
        fulfilledObligationJson.as[Obligation] shouldBe fulfilledObligation

      "ignore fields that are not on the model" in:
        val withExtraFields = Json.parse(
          """
            |{
            |  "periodKey": "19AA",
            |  "start": "2019-01-01",
            |  "end": "2019-01-31",
            |  "due": "2019-03-07",
            |  "status": "O",
            |  "someFutureField": "ignored"
            |}
            |""".stripMargin
        )

        withExtraFields.as[Obligation] shouldBe openObligation