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

package uk.gov.hmrc.mtdtransactionrisking.config

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec

class FeatureSwitchSpec extends UnitSpec:

  private def featureSwitch(config: String): FeatureSwitch =
    FeatureSwitch(Some(Configuration(ConfigFactory.parseString(config))))

  Seq(
    NrsSubmissionFeature -> "nrs-submission",
    NrsRetryFeature -> "nrs-retry"
  ).foreach { (feature, key) =>
    s"FeatureSwitch for $key" should:

      "be enabled when configured true" in:
        featureSwitch(s"$key.enabled = true").isEnabled(feature) shouldBe true

      "be disabled when configured false" in:
        featureSwitch(s"$key.enabled = false").isEnabled(feature) shouldBe false

      "default to disabled when there is no feature-switch config" in:
        FeatureSwitch(None).isEnabled(feature) shouldBe false

      "default to disabled when the feature-switch config does not mention it" in:
        featureSwitch("version-1.enabled = true").isEnabled(feature) shouldBe false
  }
