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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.{ActorSystem, Cancellable, Scheduler}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{verify, verifyNoInteractions, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.services.NrsService

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class NrsRetrySchedulerSpec extends UnitSpec, MockitoSugar:

  private val initialDelay =
    1.minute

  private val interval =
    30.seconds

  private def featureSwitch(enabled: Boolean): Option[Configuration] =
    Some(
      Configuration(
        ConfigFactory.parseString(
          s"nrs-retry.enabled = $enabled"
        )
      )
    )

  private def createSchedulerMocks() =
    (
      mock[ActorSystem],
      mock[Scheduler],
      mock[ApplicationLifecycle],
      mock[AppConfig],
      mock[NrsService],
      mock[Cancellable]
    )

  private def configureEnabledScheduler(
                                         actorSystem: ActorSystem,
                                         pekkoScheduler: Scheduler,
                                         appConfig: AppConfig,
                                         nrsService: NrsService,
                                         cancellable: Cancellable
                                       ): Unit =
    when(appConfig.featureSwitch)
      .thenReturn(featureSwitch(enabled = true))

    when(appConfig.nrsRetryInitialDelay)
      .thenReturn(initialDelay)

    when(appConfig.nrsRetryInterval)
      .thenReturn(interval)

    when(actorSystem.scheduler)
      .thenReturn(pekkoScheduler)

    when(
      pekkoScheduler.scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(any[Runnable])(any[ExecutionContext])
    ).thenReturn(cancellable)

    when(nrsService.processDueWorkItems())
      .thenReturn(Future.successful(()))

  "NrsRetryScheduler" should:

    "schedule processing with the configured initial delay and interval when retry is enabled" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        nrsService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        nrsService,
        cancellable
      )

      new NrsRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        nrsService = nrsService
      )

      verify(pekkoScheduler).scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(any[Runnable])(any[ExecutionContext])

    "not schedule processing when the retry switch is disabled" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        nrsService,
        _
        ) = createSchedulerMocks()

      when(appConfig.featureSwitch)
        .thenReturn(featureSwitch(enabled = false))

      when(actorSystem.scheduler)
        .thenReturn(pekkoScheduler)

      new NrsRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        nrsService = nrsService
      )

      verifyNoInteractions(pekkoScheduler)

    "invoke processDueWorkItems when the scheduled task runs" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        nrsService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        nrsService,
        cancellable
      )

      new NrsRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        nrsService = nrsService
      )

      val runnableCaptor =
        ArgumentCaptor.forClass(classOf[Runnable])

      verify(pekkoScheduler).scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(runnableCaptor.capture())(any[ExecutionContext])

      runnableCaptor.getValue.run()

      verify(nrsService).processDueWorkItems()

    "cancel the scheduled task when the application stops" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        nrsService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        nrsService,
        cancellable
      )

      val stopHookCaptor =
        ArgumentCaptor.forClass(classOf[Function0[Future[Unit]]])

      new NrsRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        nrsService = nrsService
      )

      verify(lifecycle).addStopHook(stopHookCaptor.capture())

      await(stopHookCaptor.getValue.apply()) shouldBe ()

      verify(cancellable).cancel()

    "recover a failed retry-processing run" in :
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        nrsService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        nrsService,
        cancellable
      )

      when(nrsService.processDueWorkItems())
        .thenReturn(
          Future.failed(
            new RuntimeException("Mongo unavailable")
          )
        )

      new NrsRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        nrsService = nrsService
      )

      val runnableCaptor =
        ArgumentCaptor.forClass(classOf[Runnable])

      verify(pekkoScheduler).scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(runnableCaptor.capture())(any[ExecutionContext])

      runnableCaptor.getValue.run()

      verify(nrsService).processDueWorkItems()
