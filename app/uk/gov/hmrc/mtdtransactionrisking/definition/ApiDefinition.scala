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

package uk.gov.hmrc.mtdtransactionrisking.definition

import play.api.libs.json.{JsString, Json, OWrites, Writes}

enum APIStatus:
  case ALPHA, BETA, STABLE, DEPRECATED, RETIRED

object APIStatus:

  def parse(value: String): Option[APIStatus] =
    values.find(_.toString == value.trim.toUpperCase)

  given Writes[APIStatus] = Writes(status => JsString(status.toString))

final case class APIVersion(version: String, status: APIStatus, endpointsEnabled: Boolean):
  require(version.nonEmpty, "version is required")

object APIVersion:
  given OWrites[APIVersion] = Json.writes[APIVersion]

final case class APIDefinition(
                                name: String,
                                description: String,
                                context: String,
                                categories: Seq[String],
                                versions: Seq[APIVersion]
                              ):
  require(name.nonEmpty, "name is required")
  require(description.nonEmpty, "description is required")
  require(context.nonEmpty, "context is required")
  require(categories.nonEmpty, "at least one category is required")
  require(versions.nonEmpty, "at least one version is required")
  require(versions.map(_.version).distinct.size == versions.size, "version numbers must be unique")

object APIDefinition:
  given OWrites[APIDefinition] = Json.writes[APIDefinition]

final case class Definition(api: APIDefinition)

object Definition:
  given OWrites[Definition] = Json.writes[Definition]