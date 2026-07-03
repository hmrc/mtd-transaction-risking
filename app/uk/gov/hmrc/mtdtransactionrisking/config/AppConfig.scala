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

@Singleton
class AppConfig @Inject()(config: ServicesConfig, configuration: Configuration):

  val appName: String = config.getString("appName")


//  //START OF RDS PLACEHOLDER
//  private val RDSConfig = configuration.get[Configuration]("microservice.services.rds")
//  val rdsBaseUrl: String = config.baseUrl("rds") + RDSConfig.get[String]("rds-url")
//  //END OF RDS PLACEHOLDER

 
  def featureSwitch: Option[Configuration] = configuration.getOptional[Configuration](s"feature-switch")

  val apiGatewayContext: String                    = config.getString("api.gateway.context")
  def apiStatus(version: String): String           = config.getString(s"api.$version.status")
  def endpointsEnabled(version: String): Boolean   = config.getBoolean(s"feature-switch.version-$version.enabled")

  // insights-proxy
  private val insightsProxyConfig = configuration.get[Configuration]("microservice.services.insights-proxy")
  val insightsProxyServiceBaseUrl: String = config.baseUrl("insights-proxy") + insightsProxyConfig.get[String]("submit-url")

  // acknowledge-proxy
  private val acknowledgeProxyConfig = configuration.get[Configuration]("microservice.services.acknowledge-proxy")
  val acknowledgeProxyServiceBaseUrl: String = config.baseUrl("acknowledge-proxy") + acknowledgeProxyConfig.get[String]("submit-url")


  // feedback endpoint for external test
  val feedbackStubBaseUrl: Option[String] =
    configuration
      .getOptional[Configuration]("microservice.services.feedback-stub")
      .map(feedbackConfig => config.baseUrl("feedback-stub") + feedbackConfig.get[String]("submit-url"))

  val feedbackEnvironmentHeaders: Option[Seq[String]] =
    configuration.getOptional[Seq[String]](
      "microservice.services.feedback-stub.environmentHeaders"
    )