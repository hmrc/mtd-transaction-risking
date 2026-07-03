package uk.gov.hmrc.mtdtransactionrisking.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.openqa.selenium.devtools.v116.network.model.ReportId
import play.api.http.Status.OK
import play.api.libs.json.Json

object AcknowledgeStub:

  private val acknowledgeUrl = "/acknowledge"

  def successResponse(vrn:String, reportId: String, correlationId: String, presentedDateTime: String): StubMapping =
    stubFor(
      post(urlEqualTo(acknowledgeUrl))
        .withRequestBody(equalToJson(
          Json.obj("vrn" -> vrn, "reportId" -> reportId, "correlationId" -> correlationId, "presentedDateTime" -> presentedDateTime).toString
        ))
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj().toString)

        )
    )
  def noReportId(vrn: String, correlationId: String, presentedDateTime: String): StubMapping =
    stubFor(
      post(urlEqualTo(acknowledgeUrl))
        .withRequestBody(equalToJson(
          Json.obj("vrn" -> vrn, "correlationId" -> correlationId, "presentedDateTime" -> presentedDateTime).toString
        ))
        .willReturn(
          aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.obj("code" -> "FORMAT_Recipt_ID", "message" -> "The provided Report ID is invalid").toString)
        )
    )