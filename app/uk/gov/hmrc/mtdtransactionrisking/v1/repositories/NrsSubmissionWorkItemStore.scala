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

package uk.gov.hmrc.mtdtransactionrisking.v1.repositories

import com.google.inject.ImplementedBy
import org.bson.types.ObjectId
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mtdtransactionrisking.v1.models.request.nrs.{NrsSubmission, NrsSubmissionWorkItem}

import scala.concurrent.Future

@ImplementedBy(classOf[NrsSubmissionWorkItemRepository])
trait NrsSubmissionWorkItemStore:
  def enqueueRetryableFailure(
                               submission: NrsSubmission
                             ): Future[WorkItem[NrsSubmissionWorkItem]]

  def enqueuePermanentFailure(
                               submission: NrsSubmission
                             ): Future[WorkItem[NrsSubmissionWorkItem]]

  def pullDue(): Future[Option[WorkItem[NrsSubmissionWorkItem]]]

  def markRetryableFailure(
                            workItem: WorkItem[NrsSubmissionWorkItem]
                          ): Future[Boolean]

  def markPermanentFailure(
                            workItem: WorkItem[NrsSubmissionWorkItem]
                          ): Future[Boolean]

  def completeAndDelete(id: ObjectId): Future[Boolean]
