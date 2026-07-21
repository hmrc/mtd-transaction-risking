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

import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.definitions.APIStatus.ALPHA
import uk.gov.hmrc.mtdtransactionrisking.definitions.Versions.VERSION_1

class ApiDefinitionSpec extends UnitSpec:

  val apiVersion: APIVersion =
    APIVersion(VERSION_1, ALPHA, endpointsEnabled = false)

  val apiDefinition: APIDefinition =
    APIDefinition(
      "VAT Assist (MTD)",
      "A service that identifies potential inaccuracies in returns before submission.",
      "context",
      Seq("VAT_MTD"),
      Seq(apiVersion),
      requiresTrust = Some(false)
    )

  private val apiVersionJson = Json.parse(
    """
      {
        "version": "1.0",
        "status": "ALPHA",
        "endpointsEnabled": false
      }
    """
  )

  private val apiDefinitionJson = Json.parse(
    """
      {
        "name": "VAT Assist (MTD)",
        "description": "A service that identifies potential inaccuracies in returns before submission.",
        "context": "context",
        "categories": ["VAT_MTD"],
        "versions": [
          {
            "version": "1.0",
            "status": "ALPHA",
            "endpointsEnabled": false
          }
        ],
        "requiresTrust": false
      }
    """
  )

  private val definitionJson = Json.parse(
    """
      {
        "api": {
          "name": "VAT Assist (MTD)",
          "description": "A service that identifies potential inaccuracies in returns before submission.",
          "context": "context",
          "categories": ["VAT_MTD"],
          "versions": [
            {
              "version": "1.0",
              "status": "ALPHA",
              "endpointsEnabled": false
            }
          ],
          "requiresTrust": false
        }
      }
    """
  )

  "APIDefinition" when {
    "the 'name' parameter is empty" should {
      "throw an IllegalArgumentException" in {
        assertThrows[IllegalArgumentException](apiDefinition.copy(name = ""))
      }
    }

    "the 'description' parameter is empty" should {
      "throw an IllegalArgumentException" in {
        assertThrows[IllegalArgumentException](apiDefinition.copy(description = ""))
      }
    }

    "the 'context' parameter is empty" should {
      "throw an IllegalArgumentException" in {
        assertThrows[IllegalArgumentException](apiDefinition.copy(context = ""))
      }
    }

    "the 'categories' parameter is empty" should {
      "throw an IllegalArgumentException" in {
        assertThrows[IllegalArgumentException](apiDefinition.copy(categories = Seq()))
      }
    }

    "the 'versions' parameter is empty" should {
      "throw an IllegalArgumentException" in {
        assertThrows[IllegalArgumentException](apiDefinition.copy(versions = Seq()))
      }
    }

    "the 'versions' parameter contains duplicates" should {
      "throw an IllegalArgumentException" in {
        assertThrows[IllegalArgumentException](apiDefinition.copy(versions = Seq(apiVersion, apiVersion)))
      }
    }
  }

  "APIVersion" should {
    "deserialise from JSON" in {
      apiVersionJson.as[APIVersion] shouldBe apiVersion
    }

    "serialise to JSON" in {
      Json.toJson(apiVersion) shouldBe apiVersionJson
    }
  }

  "APIDefinition" should {
    "deserialise from JSON" in {
      apiDefinitionJson.as[APIDefinition] shouldBe apiDefinition
    }

    "serialise to JSON" in {
      Json.toJson(apiDefinition) shouldBe apiDefinitionJson
    }
  }

  "Definition" should {
    val definition = Definition(apiDefinition)

    "deserialise from JSON" in {
      definitionJson.as[Definition] shouldBe definition
    }

    "serialise to JSON" in {
      Json.toJson(definition) shouldBe definitionJson
    }
  }
