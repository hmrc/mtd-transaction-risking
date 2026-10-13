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

package uk.gov.hmrc.mtdtransactionrisking.v1.models.response

import play.api.libs.json.{JsValue, Json, Reads}

trait RdsOutputs:
  def outputs: Seq[RdsOutput]

  def feedbackId: Option[String] = stringOf("feedbackId")

  protected def stringOf(name: String): Option[String] =
    valueOf(name).flatMap(_.asOpt[String])

  def createdDttm: Option[String] = stringOf("createdDttm")

  def responseCode: Option[Int] = valueOf("responseCode").flatMap(_.asOpt[Int])

  protected def valueOf(name: String): Option[JsValue] =
    outputs.find(_.name.equalsIgnoreCase(name)).map(_.value)

  def responseMessage: Option[String] = stringOf("responseMessage")

final case class RdsOutput(name: String, value: JsValue)

object RdsOutput:
  given reads: Reads[RdsOutput] = Json.reads[RdsOutput]
