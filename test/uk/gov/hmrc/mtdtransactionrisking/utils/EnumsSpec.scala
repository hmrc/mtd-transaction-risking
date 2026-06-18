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

package uk.gov.hmrc.mtdtransactionrisking.utils

import play.api.libs.json.{Format, Json, JsError, JsString, JsSuccess}
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class EnumsSpec extends UnitSpec {

  enum Colour { case Red, Green, Blue }

  given format: Format[Colour] = Enums.format(Colour.values)

  "Enums.format" when {
    "reading a valid value" should {
      "deserialise to the correct enum member" in {
        JsString("Red").validate[Colour] shouldBe JsSuccess(Colour.Red)
        JsString("Green").validate[Colour] shouldBe JsSuccess(Colour.Green)
        JsString("Blue").validate[Colour] shouldBe JsSuccess(Colour.Blue)
      }
    }

    "reading an invalid value" should {
      "return a JsError" in {
        JsString("Purple").validate[Colour] shouldBe a[JsError]
      }
    }

    "writing an enum value" should {
      "serialise to the correct string" in {
        Json.toJson(Colour.Red)   shouldBe JsString("Red")
        Json.toJson(Colour.Green) shouldBe JsString("Green")
        Json.toJson(Colour.Blue)  shouldBe JsString("Blue")
      }
    }
  }

  "Enums.parser" when {
    "given a known value" should {
      "return the matching enum member" in {
        val parser = Enums.parser(Colour.values)
        parser.lift("Red")    shouldBe Some(Colour.Red)
        parser.lift("Green")  shouldBe Some(Colour.Green)
        parser.lift("Purple") shouldBe None
      }
    }
  }

  "Enums.reads" when {
    "given a valid JSON string" should {
      "read the correct enum member" in {
        val reads = Enums.reads(Colour.values)
        JsString("Blue").validate(reads) shouldBe JsSuccess(Colour.Blue)
      }
    }

    "given an invalid JSON string" should {
      "return a JsError" in {
        val reads = Enums.reads(Colour.values)
        JsString("Yellow").validate(reads) shouldBe a[JsError]
      }
    }
  }

  "Enums.writes" when {
    "given an enum value" should {
      "serialise to a JSON string using toString" in {
        val writes = Enums.writes[Colour]
        writes.writes(Colour.Red) shouldBe JsString("Red")
      }
    }
  }

  "Enums.readsRestricted" when {
    "given a value in the restricted set" should {
      "deserialise successfully" in {
        val reads = Enums.readsRestricted(Colour.Red, Colour.Blue)
        JsString("Red").validate(reads)  shouldBe JsSuccess(Colour.Red)
        JsString("Blue").validate(reads) shouldBe JsSuccess(Colour.Blue)
      }
    }

    "given a value not in the restricted set" should {
      "return a JsError" in {
        val reads = Enums.readsRestricted(Colour.Red, Colour.Blue)
        JsString("Green").validate(reads) shouldBe a[JsError]
      }
    }
  }

  "Shows.toStringShow" when {
    "given any value" should {
      "show it using toString" in {
        val show = Shows.toStringShow[Colour]
        show.show(Colour.Green) shouldBe "Green"
      }
    }
  }
}