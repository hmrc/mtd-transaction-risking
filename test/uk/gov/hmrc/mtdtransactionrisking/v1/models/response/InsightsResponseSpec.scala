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

import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
class InsightsResponseSpec extends UnitSpec:

  private val strategicRisk = StrategicRisk(
    riskCorrelationId = CorrelationId("abc-123"),
    riskScore = 0.75,
    reasons = Seq("VRN 'GB123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky.")
  )

  private val insights = Insights(strategicRisk = strategicRisk)
  private val fullModel = InsightsResponse(insights = insights)

  private val strategicRiskJson = Json.parse(
    """
      |{
      |  "riskCorrelationId": "abc-123",
      |  "riskScore": 0.75,
      |  "reasons": [
      |    "VRN 'GB123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky."
      |  ]
      |}
      """.stripMargin
  )

  private val insightsJson = Json.parse(
    """
      |{
      |  "strategicRisk": {
      |    "riskCorrelationId": "abc-123",
      |    "riskScore": 0.75,
      |    "reasons": [
      |      "VRN 'GB123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky."
      |    ]
      |  }
      |}
      """.stripMargin
  )

  private val fullJson = Json.parse(
    """
      |{
      |  "insights": {
      |    "strategicRisk": {
      |      "riskCorrelationId": "abc-123",
      |      "riskScore": 0.75,
      |      "reasons": [
      |        "VRN 'GB123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky."
      |      ]
      |    }
      |  }
      |}
      """.stripMargin
  )

  "StrategicRisk" when:

    "written to JSON" should:
      "generate the correct JSON" in:
        Json.toJson(strategicRisk) shouldBe strategicRiskJson

    "read from JSON" should:
      "deserialise correctly" in:
        strategicRiskJson.as[StrategicRisk] shouldBe strategicRisk

    "read from JSON containing unmapped downstream fields" should:
      "ignore them" in:
        val withRiskData = Json.parse(
          """
            |{
            |  "riskCorrelationId": "abc-123",
            |  "riskScore": 0.75,
            |  "reasons": [
            |    "VRN 'GB123456789' is 1 hops from something risky. The average VRN is 2.51 hops from something risky."
            |  ],
            |  "riskData": [ { "hops": 1, "avgHops": 2.51 } ]
            |}
             """.stripMargin
        )

        withRiskData.as[StrategicRisk] shouldBe strategicRisk

  "Insights" when:

    "written to JSON" should:
      "generate the correct JSON" in:
        Json.toJson(insights) shouldBe insightsJson

    "read from JSON" should:
      "deserialise correctly" in:
        insightsJson.as[Insights] shouldBe insights

  "InsightsResponse" when:

    "written to JSON" should:
      "generate the correct JSON" in:
        Json.toJson(fullModel) shouldBe fullJson

    "read from JSON" should:
      "deserialise correctly" in:
        fullJson.as[InsightsResponse] shouldBe fullModel