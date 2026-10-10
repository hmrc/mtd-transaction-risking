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

package uk.gov.hmrc.mtdtransactionrisking.v1.scheduling

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mtdtransactionrisking.config.{AppConfig, FeatureSwitch, NrsRetryFeature}
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.mtdtransactionrisking.v1.services.NrsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class NrsRetryScheduler @Inject()(
                                   actorSystem: ActorSystem,
                                   applicationLifecycle: ApplicationLifecycle,
                                   appConfig: AppConfig,
                                   nrsService: NrsService
                                 )(using ec: ExecutionContext)
  extends Logging:

  private val retryEnabled =
    FeatureSwitch(appConfig.featureSwitch).isEnabled(NrsRetryFeature)

  private val scheduledTask: Option[Cancellable] =
    if retryEnabled then
      logger.info(
        s"[NrsRetryScheduler] enabled; initial delay=" +
          s"${appConfig.nrsRetryInitialDelay}; interval=" +
          s"${appConfig.nrsRetryInterval}"
      )

      Some(
        actorSystem.scheduler.scheduleAtFixedRate(
          initialDelay = appConfig.nrsRetryInitialDelay,
          interval = appConfig.nrsRetryInterval
        ) { () =>
          nrsService.processDueWorkItems().recover {
            case NonFatal(error) =>
              logger.error(
                "[NrsRetryScheduler] Unexpected NRS retry worker failure",
                error
              )
              ()
          }
          ()
        }
      )
    else
      logger.info("[NrsRetryScheduler] disabled")
      None

  applicationLifecycle.addStopHook { () =>
    scheduledTask.foreach(_.cancel())
    Future.unit
  }
