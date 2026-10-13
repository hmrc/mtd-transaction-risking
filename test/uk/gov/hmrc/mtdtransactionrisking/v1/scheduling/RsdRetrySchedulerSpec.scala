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
import uk.gov.hmrc.mtdtransactionrisking.v1.services.RsdRetryService

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class RsdRetrySchedulerSpec extends UnitSpec, MockitoSugar:

  private val initialDelay =
    1.minute

  private val interval =
    30.seconds

  private def featureSwitch(
                             enabled: Boolean
                           ): Option[Configuration] =
    Some(
      Configuration(
        ConfigFactory.parseString(
          s"rsd-retry.enabled = $enabled"
        )
      )
    )

  private def createSchedulerMocks() =
    (
      mock[ActorSystem],
      mock[Scheduler],
      mock[ApplicationLifecycle],
      mock[AppConfig],
      mock[RsdRetryService],
      mock[Cancellable]
    )

  private def configureEnabledScheduler(
                                         actorSystem: ActorSystem,
                                         pekkoScheduler: Scheduler,
                                         appConfig: AppConfig,
                                         rsdRetryService: RsdRetryService,
                                         cancellable: Cancellable
                                       ): Unit =
    when(appConfig.featureSwitch)
      .thenReturn(featureSwitch(enabled = true))

    when(appConfig.rsdRetryInitialDelay)
      .thenReturn(initialDelay)

    when(appConfig.rsdRetryInterval)
      .thenReturn(interval)

    when(actorSystem.scheduler)
      .thenReturn(pekkoScheduler)

    when(
      pekkoScheduler.scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(any[Runnable])(any[ExecutionContext])
    ).thenReturn(cancellable)

    when(rsdRetryService.processDueWorkItems())
      .thenReturn(Future.successful(()))

  "RsdRetryScheduler" should:

    "schedule processing with the configured initial delay and interval when enabled" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        rsdRetryService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        rsdRetryService,
        cancellable
      )

      new RsdRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        rsdRetryService = rsdRetryService
      )

      verify(pekkoScheduler).scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(any[Runnable])(any[ExecutionContext])

    "not schedule processing when rsd-retry is disabled" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        rsdRetryService,
        _
        ) = createSchedulerMocks()

      when(appConfig.featureSwitch)
        .thenReturn(featureSwitch(enabled = false))

      when(actorSystem.scheduler)
        .thenReturn(pekkoScheduler)

      new RsdRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        rsdRetryService = rsdRetryService
      )

      verifyNoInteractions(pekkoScheduler)

    "invoke processDueWorkItems when the scheduled task runs" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        rsdRetryService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        rsdRetryService,
        cancellable
      )

      new RsdRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        rsdRetryService = rsdRetryService
      )

      val runnableCaptor =
        ArgumentCaptor.forClass(classOf[Runnable])

      verify(pekkoScheduler).scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(runnableCaptor.capture())(any[ExecutionContext])

      runnableCaptor.getValue.run()

      verify(rsdRetryService).processDueWorkItems()

    "cancel the scheduled task when the application stops" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        rsdRetryService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        rsdRetryService,
        cancellable
      )

      val stopHookCaptor =
        ArgumentCaptor.forClass(
          classOf[Function0[Future[Unit]]]
        )

      new RsdRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        rsdRetryService = rsdRetryService
      )

      verify(lifecycle).addStopHook(
        stopHookCaptor.capture()
      )

      await(
        stopHookCaptor.getValue.apply()
      ) shouldBe ()

      verify(cancellable).cancel()

    "recover a failed retry processing run so later ticks may continue" in:
      val (
        actorSystem,
        pekkoScheduler,
        lifecycle,
        appConfig,
        rsdRetryService,
        cancellable
        ) = createSchedulerMocks()

      configureEnabledScheduler(
        actorSystem,
        pekkoScheduler,
        appConfig,
        rsdRetryService,
        cancellable
      )

      when(rsdRetryService.processDueWorkItems())
        .thenReturn(
          Future.failed(
            new RuntimeException("Mongo unavailable")
          )
        )

      new RsdRetryScheduler(
        actorSystem = actorSystem,
        applicationLifecycle = lifecycle,
        appConfig = appConfig,
        rsdRetryService = rsdRetryService
      )

      val runnableCaptor =
        ArgumentCaptor.forClass(classOf[Runnable])

      verify(pekkoScheduler).scheduleAtFixedRate(
        eqTo(initialDelay),
        eqTo(interval)
      )(runnableCaptor.capture())(any[ExecutionContext])

      runnableCaptor.getValue.run()

      verify(rsdRetryService).processDueWorkItems()
