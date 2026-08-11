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

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

trait AppConfig:

  def appName: String
  def featureSwitch: Option[Configuration]
  def apiGatewayContext: String
  def apiStatus(version: String): String
  def endpointsEnabled(version: String): Boolean

  def feedbackStubBaseUrl: Option[String]
  def feedbackEnvironmentHeaders: Option[Seq[String]]

  def vatApiBaseUrl: String

  def insightsProxyServiceBaseUrl: String

  def reportGenerationBaseUrl: String

  def acknowledgeStubBaseUrl: String
  def acknowledgeEnvironmentHeaders: Option[Seq[String]]

@Singleton
class AppConfigImpl @Inject() (config: ServicesConfig, configuration: Configuration) extends AppConfig:

  val appName: String = config.getString("appName")

  def featureSwitch: Option[Configuration] = configuration.getOptional[Configuration]("feature-switch")

  val apiGatewayContext: String                  = config.getString("api.gateway.context")
  def apiStatus(version: String): String         = config.getString(s"api.$version.status")
  def endpointsEnabled(version: String): Boolean = config.getBoolean(s"feature-switch.version-$version.enabled")

  // feedback stub for external test
  val feedbackStubBaseUrl: Option[String] =
    configuration
      .getOptional[Configuration]("microservice.services.feedback-stub")
      .map(feedbackConfig => config.baseUrl("feedback-stub") + feedbackConfig.get[String]("submit-url"))

  val feedbackEnvironmentHeaders: Option[Seq[String]] =
    configuration.getOptional[Seq[String]]("microservice.services.feedback-stub.environmentHeaders")

  // vat-api validation
  private val vatApiConfig  = configuration.get[Configuration]("microservice.services.vat-api")
  val vatApiBaseUrl: String = config.baseUrl("vat-api") + vatApiConfig.get[String]("submit-url")

  // insights-proxy
  private val insightsProxyConfig         = configuration.get[Configuration]("microservice.services.insights-proxy")
  val insightsProxyServiceBaseUrl: String = config.baseUrl("insights-proxy") + insightsProxyConfig.get[String]("submit-url")

  // report generation
  private val reportGenerationConfig  = configuration.get[Configuration]("microservice.services.report-generation")
  val reportGenerationBaseUrl: String = config.baseUrl("report-generation") + reportGenerationConfig.get[String]("submit-url")

  // acknowledge stub endpoint for external test
  private val acknowledgeStubConfig  = configuration.get[Configuration]("microservice.services.acknowledge-stub")
  val acknowledgeStubBaseUrl: String = config.baseUrl("acknowledge-stub") + acknowledgeStubConfig.get[String]("submit-url")

  val acknowledgeEnvironmentHeaders: Option[Seq[String]] =
    configuration.getOptional[Seq[String]]("microservice.services.acknowledge-stub.environmentHeaders")