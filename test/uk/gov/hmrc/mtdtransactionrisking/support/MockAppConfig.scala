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

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig

trait MockAppConfig extends MockitoSugar {

  val mockAppConfig: AppConfig = mock[AppConfig]

  object MockedAppConfig {

    def featureSwitch(returns: Option[Configuration]): Unit =
      when(mockAppConfig.featureSwitch).thenReturn(returns)

    def apiGatewayContext(returns: String): Unit =
      when(mockAppConfig.apiGatewayContext).thenReturn(returns)

    def apiStatus(version: String)(returns: String): Unit =
      when(mockAppConfig.apiStatus(version)).thenReturn(returns)

    def endpointsEnabled(version: String)(returns: Boolean): Unit =
      when(mockAppConfig.endpointsEnabled(version)).thenReturn(returns)

    def cipRiskServiceBaseUrl(returns: String): Unit =
      when(mockAppConfig.insightsProxyServiceBaseUrl).thenReturn(returns)

    def appName(returns: String): Unit =
      when(mockAppConfig.appName).thenReturn(returns)
  }
}