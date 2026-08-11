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

package uk.gov.hmrc.mtdtransactionrisking.definitions

import uk.gov.hmrc.mtdtransactionrisking.definitions.APIStatus.{ALPHA, BETA}
import uk.gov.hmrc.mtdtransactionrisking.definitions.Versions.VERSION_1
import uk.gov.hmrc.mtdtransactionrisking.support.{MockAppConfig, UnitSpec}

class ApiDefinitionFactorySpec extends UnitSpec, MockAppConfig:

  "buildAPIStatus" when:

    "the configured status is valid" should:
      "return the matching APIStatus" in:
        MockedAppConfig.apiGatewayContext.returns("misc/transaction-risking").anyNumberOfTimes()
        MockedAppConfig.apiStatus(VERSION_1).returns("BETA").anyNumberOfTimes()
        MockedAppConfig.endpointsEnabled("1").returns(true).anyNumberOfTimes()

        val factory = new ApiDefinitionFactory(mockAppConfig)
        factory.buildAPIStatus(VERSION_1) shouldBe BETA

    "the configured status is invalid" should:
      "default to ALPHA" in:
        MockedAppConfig.apiGatewayContext.returns("misc/transaction-risking").anyNumberOfTimes()
        MockedAppConfig.apiStatus(VERSION_1).returns("INVALID").anyNumberOfTimes()
        MockedAppConfig.endpointsEnabled("1").returns(true).anyNumberOfTimes()

        val factory = new ApiDefinitionFactory(mockAppConfig)
        factory.buildAPIStatus(VERSION_1) shouldBe ALPHA

  "definition" should:
    "return a Definition with the correct API metadata" in:
      MockedAppConfig.apiGatewayContext.returns("misc/transaction-risking").anyNumberOfTimes()
      MockedAppConfig.apiStatus(VERSION_1).returns("BETA").anyNumberOfTimes()
      MockedAppConfig.endpointsEnabled("1").returns(true).anyNumberOfTimes()

      val factory = new ApiDefinitionFactory(mockAppConfig)
      val result  = factory.definition

      result.api.name shouldBe "VAT Assist (MTD)"
      result.api.context shouldBe "misc/transaction-risking"
      result.api.categories shouldBe Seq("VAT_MTD")
      result.api.versions should have size 1
      result.api.versions.head.version shouldBe VERSION_1
      result.api.versions.head.endpointsEnabled shouldBe true