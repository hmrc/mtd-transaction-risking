package uk.gov.hmrc.mtdtransactionrisking.v1.models.request

import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class ObligationsRequestSpec extends UnitSpec:

  "ObligationsRequest" when:
    "written to JSON" should:
      "produce VRN and periodKey fields" in:
        val request = ObligationsRequest("123456789", "24AA")
        val json = Json.toJson(request)
        (json \ "VRN").as[String] shouldBe "123456789"
        (json \ "periodKey").as[String] shouldBe "24AA"
