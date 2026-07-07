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

package uk.gov.hmrc.mtdtransactionrisking.connector

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.support.IntegrationBaseSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector
import uk.gov.hmrc.mtdtransactionrisking.v1.connectors.AcknowledgeConnector.AcknowledgeConnectorError
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.AcknowledgeRequest

import java.time.OffsetDateTime

class AcknowledgeConnectorISpec extends IntegrationBaseSpec:

  override def servicesConfig: Map[String, Any] =
    super.servicesConfig ++ Map(
      "microservice.services.acknowledge-stub.host"       -> mockHost,
      "microservice.services.acknowledge-stub.port"       -> mockPort,
      "microservice.services.acknowledge-stub.submit-url" -> "/acknowledge"
    )

  override def beforeEach(): Unit =
    super.beforeEach()
    resetWireMock()

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val connector                  = app.injector.instanceOf[AcknowledgeConnector]

  private val request = AcknowledgeRequest(
    vrn = "123456789",
    reportId = "123456aB-012A-345B-678C-012345678abc",
    correlationId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253",
    presentedDateTime = OffsetDateTime.parse("2026-07-07T15:32:00Z")
  )

  "AcknowledgeConnector.acknowledge" should:

    "return Right(()) when upstream returns 204" in:
      stubFor(
        post(urlEqualTo("/acknowledge"))
          .withRequestBody(equalToJson(Json.toJson(request).toString))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(connector.acknowledge(request).value) shouldBe Right(())

    "return Right(()) when upstream returns 200" in:
      stubFor(
        post(urlEqualTo("/acknowledge"))
          .withRequestBody(equalToJson(Json.toJson(request).toString))
          .willReturn(aResponse().withStatus(OK))
      )

      await(connector.acknowledge(request).value) shouldBe Right(())

    "return parsed upstream error when error body is valid JSON" in:
      stubFor(
        post(urlEqualTo("/acknowledge"))
          .withRequestBody(equalToJson(Json.toJson(request).toString))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.obj("code" -> "FORMAT_VRN", "message" -> "The provided Vrn is invalid.").toString)
          )
      )

      await(connector.acknowledge(request).value) shouldBe
        Left(AcknowledgeConnectorError(BAD_REQUEST, "FORMAT_VRN", "The provided Vrn is invalid."))

    "return INTERNAL_SERVER_ERROR fallback when error body is not parseable" in:
      stubFor(
        post(urlEqualTo("/acknowledge"))
          .withRequestBody(equalToJson(Json.toJson(request).toString))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withHeader("Content-Type", "text/plain")
              .withBody("not-json")
          )
      )

      await(connector.acknowledge(request).value) shouldBe
        Left(AcknowledgeConnectorError(BAD_REQUEST, "INTERNAL_SERVER_ERROR", "An unexpected error occurred."))

    "return INTERNAL_SERVER_ERROR when an exception occurs" in:
      stopWireMock()
      try
        await(connector.acknowledge(request).value) shouldBe
          Left(AcknowledgeConnectorError(INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred."))
      finally
        startWireMock()