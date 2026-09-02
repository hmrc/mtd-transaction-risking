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

package uk.gov.hmrc.mtdtransactionrisking.support

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import play.api.Configuration
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.{InteractionCredentials, RdsCredentials}

trait MockAppConfig extends MockFactory:
  this: TestSuite =>

  val mockAppConfig: AppConfig = mock[AppConfig]

  object MockedAppConfig:

    def appName: CallHandler[String] =
      (() => mockAppConfig.appName).expects()

    def featureSwitch: CallHandler[Option[Configuration]] =
      (() => mockAppConfig.featureSwitch).expects()

    def apiGatewayContext: CallHandler[String] =
      (() => mockAppConfig.apiGatewayContext).expects()

    def apiStatus(version: String): CallHandler[String] =
      (mockAppConfig.apiStatus(_: String)).expects(version)

    def endpointsEnabled(version: String): CallHandler[Boolean] =
      (mockAppConfig.endpointsEnabled(_: String)).expects(version)

    def feedbackStubBaseUrl: CallHandler[Option[String]] =
      (() => mockAppConfig.feedbackStubBaseUrl).expects()

    def feedbackEnvironmentHeaders: CallHandler[Option[Seq[String]]] =
      (() => mockAppConfig.feedbackEnvironmentHeaders).expects()

    def vatApiBaseUrl: CallHandler[String] =
      (() => mockAppConfig.vatApiBaseUrl).expects()

    def insightsProxyServiceBaseUrl: CallHandler[String] =
      (() => mockAppConfig.insightsProxyServiceBaseUrl).expects()

    def acknowledgeStubBaseUrl: CallHandler[String] =
      (() => mockAppConfig.acknowledgeStubBaseUrl).expects()

    def acknowledgeEnvironmentHeaders: CallHandler[Option[Seq[String]]] =
      (() => mockAppConfig.acknowledgeEnvironmentHeaders).expects()

    def rdsSubmitUrl: CallHandler[String] =
      (() => mockAppConfig.rdsSubmitUrl).expects()

    def rdsAcknowledgeUrl: CallHandler[String] =
      (() => mockAppConfig.rdsAcknowledgeUrl).expects()

    def rdsAuthRequired: CallHandler[Boolean] =
      (() => mockAppConfig.rdsAuthRequired).expects()

    def rdsAuthUrl: CallHandler[String] =
      (() => mockAppConfig.rdsAuthUrl).expects()

    def rdsCredentials: CallHandler[RdsCredentials] =
      (() => mockAppConfig.rdsCredentials).expects()

    def interactionsBaseUrl: CallHandler[String] =
      (() => mockAppConfig.interactionsBaseUrl).expects()

    def interactionCredentials: CallHandler[InteractionCredentials] =
      (() => mockAppConfig.interactionCredentials).expects()  