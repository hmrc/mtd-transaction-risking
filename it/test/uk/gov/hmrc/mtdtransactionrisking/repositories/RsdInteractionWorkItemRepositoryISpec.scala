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

package uk.gov.hmrc.mtdtransactionrisking.repositories

import org.bson.types.ObjectId
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.model.{Filters, Updates}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem, WorkItemFields}
import uk.gov.hmrc.mtdtransactionrisking.config.AppConfig
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.{Interaction, InteractionMetadata, InteractionPayload}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.rsd.RsdInteractionWorkItem
import uk.gov.hmrc.mtdtransactionrisking.v1.repositories.RsdInteractionWorkItemRepository
import uk.gov.hmrc.mtdtransactionrisking.v1.services.RsdRetryPolicy

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class RsdInteractionWorkItemRepositoryISpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach
    with DefaultPlayMongoRepositorySupport[WorkItem[RsdInteractionWorkItem]]
    with MockFactory:

  private val maxRetries =
    10

  private val initialNow =
    Instant.parse("2026-10-06T10:00:00Z")

  private val clock =
    new RsdMutableClock(initialNow)

  private val appConfig =
    stub[AppConfig]

  (() => appConfig.rsdRetryRetention)
    .when()
    .returns(28.days)

  (() => appConfig.rsdRetryInProgressRetryAfter)
    .when()
    .returns(5.minutes)

  private object TestRetryPolicy extends RsdRetryPolicy:

    override val maxRetries: Int =
      RsdInteractionWorkItemRepositoryISpec.this.maxRetries

    override def delayForRetry(
                                retryNumber: Int
                              ): FiniteDuration =
      require(
        retryNumber >= 1 && retryNumber <= maxRetries
      )

      (5.minutes.toMillis * Math.pow(2.0, retryNumber - 1))
        .toLong
        .millis

    override def isExhaustedAfterFailure(
                                          failureCount: Int
                                        ): Boolean =
      failureCount >= maxRetries
  
  override protected val repository: RsdInteractionWorkItemRepository =
    new RsdInteractionWorkItemRepository(
      mongoComponent = mongoComponent,
      appConfig = appConfig,
      retryPolicy = TestRetryPolicy,
      clock = clock
    )

  private val correlationId =
    CorrelationId("bd6c0972-34e7-11f1-9715-f35b3eb40b52")

  private val interaction =
    Interaction(
      serviceRegime = "vat-assist",
      eventName = "generate-report",
      feedbackId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
      eventTimestamp = initialNow.toString,
      metadata = Seq(
        InteractionMetadata(
          vrn = "123456789",
          start = Some("2026-07-01"),
          end = Some("2026-09-30"),
          additionalProperties = Json.obj(
            "periodKey" -> "26A3"
          )
        )
      ),
      payload = InteractionPayload(
        reportId = "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
        messages = None
      )
    )

  private val payload =
    RsdInteractionWorkItem(
      interaction = interaction,
      correlationId = correlationId
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    clock.setInstant(initialNow)

  private def findStored(
                          id: ObjectId
                        ): Option[WorkItem[RsdInteractionWorkItem]] =
    repository
      .collection
      .find(
        Filters.equal(
          WorkItemFields.default.id,
          id
        )
      )
      .toFuture()
      .futureValue
      .headOption

  private def createAndClaimDueWorkItem(): WorkItem[RsdInteractionWorkItem] =
    repository
      .pushNew(
        item = payload,
        availableAt = initialNow.minusSeconds(1)
      )
      .futureValue

    repository
      .pullDue()
      .futureValue
      .value

  private def setFailureCount(
                               id: ObjectId,
                               value: Int
                             ): Unit =
    repository
      .collection
      .updateOne(
        Filters.equal(
          WorkItemFields.default.id,
          id
        ),
        Updates.set(
          WorkItemFields.default.failureCount,
          value
        )
      )
      .toFuture()
      .futureValue

  "RsdInteractionWorkItemRepository" should :

    "persist a retryable initial failure with failureCount zero and retry one due after five minutes" in :
      val persisted =
        repository
          .enqueueRetryableFailure(
            interaction = interaction,
            correlationId = correlationId
          )
          .futureValue

      persisted.item shouldBe payload
      persisted.status shouldBe ProcessingStatus.ToDo
      persisted.failureCount shouldBe 0
      persisted.availableAt shouldBe
        initialNow.plusSeconds(5.minutes.toSeconds)

    "persist an initial permanent failure and never return it as due" in :
      val persisted =
        repository
          .enqueuePermanentFailure(
            interaction = interaction,
            correlationId = correlationId
          )
          .futureValue

      persisted.item shouldBe payload
      persisted.status shouldBe ProcessingStatus.PermanentlyFailed
      persisted.failureCount shouldBe 0
      persisted.updatedAt shouldBe initialNow

      repository.pullDue().futureValue shouldBe None

    "round trip the original Interaction and CorrelationId" in :
      val persisted =
        repository
          .enqueueRetryableFailure(
            interaction = interaction,
            correlationId = correlationId
          )
          .futureValue

      val stored =
        findStored(persisted.id).value

      stored.item.interaction shouldBe interaction
      stored.item.correlationId shouldBe correlationId

    "atomically claim one due item as InProgress" in :
      val stored =
        repository
          .pushNew(
            item = payload,
            availableAt = initialNow.minusSeconds(1)
          )
          .futureValue

      val claimed =
        repository
          .pullDue()
          .futureValue
          .value

      claimed.id shouldBe stored.id
      claimed.status shouldBe ProcessingStatus.InProgress
      claimed.item shouldBe payload

    "not claim the same due item twice across concurrent workers" in :
      repository
        .pushNew(
          item = payload,
          availableAt = initialNow.minusSeconds(1)
        )
        .futureValue

      val claims =
        Future.sequence(
          Seq(
            repository.pullDue(),
            repository.pullDue()
          )
        ).futureValue

      claims.flatten should have size 1
      claims.flatten.head.status shouldBe ProcessingStatus.InProgress

    "schedule retry two after retry one fails" in :
      val claimed =
        createAndClaimDueWorkItem()

      claimed.failureCount shouldBe 0

      repository
        .markRetryableFailure(claimed)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.Failed
      stored.failureCount shouldBe 1
      stored.availableAt shouldBe
        initialNow.plusSeconds(10.minutes.toSeconds)

    "schedule retry ten after retry nine fails" in :
      val claimed =
        createAndClaimDueWorkItem()

      setFailureCount(
        id = claimed.id,
        value = 8
      )

      val retryNineWorkItem =
        claimed.copy(
          failureCount = 8
        )

      repository
        .markRetryableFailure(retryNineWorkItem)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.Failed
      stored.failureCount shouldBe 9
      stored.availableAt shouldBe
        initialNow.plusSeconds(2560.minutes.toSeconds)

    "mark the item PermanentlyFailed after retry ten fails and never schedule retry eleven" in :
      val claimed =
        createAndClaimDueWorkItem()

      setFailureCount(
        id = claimed.id,
        value = 9
      )

      val retryTenWorkItem =
        claimed.copy(
          failureCount = 9
        )

      repository
        .markRetryableFailure(retryTenWorkItem)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.PermanentlyFailed
      stored.failureCount shouldBe 10

      repository.pullDue().futureValue shouldBe None

    "mark an in-progress item permanently failed after a permanent result" in :
      val claimed =
        createAndClaimDueWorkItem()

      repository
        .markPermanentFailure(claimed)
        .futureValue shouldBe true

      val stored =
        findStored(claimed.id).value

      stored.status shouldBe ProcessingStatus.PermanentlyFailed

    "delete a successfully retried work item" in :
      val claimed =
        createAndClaimDueWorkItem()

      repository
        .completeAndDelete(claimed.id)
        .futureValue shouldBe true

      findStored(claimed.id) shouldBe None

private class RsdMutableClock(
                               initial: Instant
                             ) extends Clock:

  private var current =
    initial

  def setInstant(
                  instant: Instant
                ): Unit =
    current = instant

  override def getZone: ZoneId =
    ZoneOffset.UTC

  override def withZone(
                         zone: ZoneId
                       ): Clock =
    this

  override def instant(): Instant =
    current
