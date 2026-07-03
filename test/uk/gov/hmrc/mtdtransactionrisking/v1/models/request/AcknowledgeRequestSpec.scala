package uk.gov.hmrc.mtdtransactionrisking.v1.models.request
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.OFormat.oFormatFromReadsAndOWrites
import play.api.libs.json.{JsObject, JsSuccess, Json}
import play.api.libs.json.Format.GenericFormat

class AcknowledgeRequestSpec extends AnyWordSpec with Matchers {
  val validJson: JsObject = Json.obj(
    "vrn"               -> "123456789",
    "reportId"          -> "123456aB-012A-345B-678C-012345678abc",
    "correlationId"     -> "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
    "presentedDateTime" -> "2023-06-01T12:00:00Z"
  )

  val expectedModel = AcknowledgeRequest(
    vrn               = "123456789",
    reportId          = "123456aB-012A-345B-678C-012345678abc",
    correlationId     = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
    presentedDateTime = java.time.OffsetDateTime.parse("2023-06-01T12:00:00Z")
  )

  "AcknowledgeRequest" when {

    "reading from valid JSON" should {

      "deserialise correctly" in {
        validJson.validate[AcknowledgeRequest] shouldBe JsSuccess(expectedModel)
      }
    }
    
    

  }
}