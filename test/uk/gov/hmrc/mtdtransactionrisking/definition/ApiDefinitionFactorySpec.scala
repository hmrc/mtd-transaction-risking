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

package uk.gov.hmrc.mtdtransactionrisking.definition

import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig

class ApiDefinitionFactorySpec extends AnyWordSpec with Matchers with MockitoSugar:

  val appConfig: AppConfig = mock[AppConfig]

  private def factoryWith(status: String, endpointsEnabled: Boolean = false): ApiDefinitionFactory =
    when(appConfig.apiGatewayContext).thenReturn("organisations/vat-assist")
    when(appConfig.apiStatus("1.0")).thenReturn(status)
    when(appConfig.endpointsEnabled("1.0")).thenReturn(endpointsEnabled)
    new ApiDefinitionFactory(appConfig)

  "definition" should:

    "use the status and endpointsEnabled from config" in:
      val version = factoryWith("BETA", endpointsEnabled = true).definition.api.versions.head

      version.status shouldBe APIStatus.BETA
      version.endpointsEnabled shouldBe true

    "write the same JSON shape the API platform expects" in:
      Json.toJson(factoryWith("ALPHA").definition) shouldBe Json.parse(
        """{
          |  "api": {
          |    "name": "VAT Assist (MTD)",
          |    "description": "A service that identifies potential inaccuracies in returns before submission.",
          |    "context": "organisations/vat-assist",
          |    "categories": ["VAT_MTD"],
          |    "versions": [{ "version": "1.0", "status": "ALPHA", "endpointsEnabled": false }]
          |  }
          |}""".stripMargin
      )

  "buildAPIStatus" should:

    "accept every status, ignoring case" in:
      Seq("alpha" -> APIStatus.ALPHA, "Beta" -> APIStatus.BETA, "STABLE" -> APIStatus.STABLE).foreach { (configured, expected) =>
        factoryWith(configured).buildAPIStatus("1.0") shouldBe expected
      }

    "fall back to ALPHA when the configured status is not valid" in:
      factoryWith("BETTA").buildAPIStatus("1.0") shouldBe APIStatus.ALPHA