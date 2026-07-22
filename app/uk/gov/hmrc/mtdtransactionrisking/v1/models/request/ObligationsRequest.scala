package uk.gov.hmrc.mtdtransactionrisking.v1.models.request
import play.api.libs.json.{Json, OWrites}

case class ObligationsRequest(VRN: String, periodKey: String)

object ObligationsRequest:

  given writes: OWrites[ObligationsRequest] = Json.writes[ObligationsRequest]
