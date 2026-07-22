package uk.gov.hmrc.mtdtransactionrisking.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.*
import play.api.libs.json.Json

object ObligationsStub:

  private def obligationsUrl(vrn: String) =
    s"/enterprise/obligation-data/vrn/$vrn/VATC?status=O"

  def successResponse(vrn: String, periodKey: String, from: String, to: String): StubMapping =
    stubFor(
      post(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(
              Json.obj(
                "obligations" -> Json.arr(
                  Json.obj(
                    "identification" -> Json.obj(
                      "referenceNumber" -> "AB123",
                      "referenceType"   -> "MTDBIS",
                      "incomeSourceType" -> "ITSA"
                    ),
                    "obligationDetails" -> Json.arr(
                      Json.obj(
                        "status"                           -> "O",
                        "inboundCorrespondenceFromDate"    -> from,
                        "inboundCorrespondenceToDate"      -> to,
                        "inboundCorrespondenceDueDate"     -> to,
                        "inboundCorrespondenceDateReceived"-> from,
                        "periodKey"                        -> periodKey
                      )
                    )
                  )
                )
              ).toString()
            )
        )
    )

  def malformedSuccessResponse(vrn: String): StubMapping =
    stubFor(
      post(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"unexpected":"shape"}""")
        )
    )

  def singleFailureResponse(vrn: String): StubMapping =
    stubFor(
      post(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"code":"INVALID_DATE_FROM","reason":"Invalid date"}""")
        )
    )

  def arrayFailureResponse(vrn: String): StubMapping =
    stubFor(
      post(urlEqualTo(obligationsUrl(vrn)))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{"failures":[{"code":"INVALID_DATE_FROM","reason":"Invalid date"},{"code":"INVALID_STATUS","reason":"Invalid status"}]}"""
            )
        )
    )