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

package uk.gov.hmrc.mtdtransactionrisking.v1.controllers.auth

import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.mvc.Results.{BadRequest, Forbidden, Unauthorized}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.*
import uk.gov.hmrc.auth.core.retrieve.{ItmpAddress, ItmpName, ~}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.IdGenerator.CorrelationId
import uk.gov.hmrc.mtdtransactionrisking.utils.{IdGenerator, Logging}
import uk.gov.hmrc.mtdtransactionrisking.v1.models.auth.IdentityData
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.{ErrorWrapper, VrnFormatError}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class AuthenticatedVATRequest[A](
                                       request: Request[A],
                                       internalId: String,
                                       vrn: String,
                                       arn: Option[String],
                                       identityData: Option[IdentityData]
                                     ) extends WrappedRequest[A](request)

class VATAuthAction @Inject() (override val authConnector: AuthConnector, configuration: Configuration, bodyParser: BodyParsers.Default)(using
    ec: ExecutionContext)
    extends AuthorisedFunctions
    with Logging:

  private val authEnabled: Boolean =
    configuration
      .getOptional[Boolean]("feature-switch.auth.enabled")
      .getOrElse(true)

  private val vrnRegex = """^\d{9}$"""

  def authorisedFor(requestedVRN: String): ActionBuilder[AuthenticatedVATRequest, AnyContent] =
    new ActionBuilder[AuthenticatedVATRequest, AnyContent]:

      override protected def executionContext: ExecutionContext = ec

      override def parser: BodyParser[AnyContent] = bodyParser

      override def invokeBlock[A](request: Request[A], block: AuthenticatedVATRequest[A] => Future[Result]): Future[Result] =
        given hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        val correlationId: CorrelationId = IdGenerator.generateId()

        if !authEnabled then
          logger.warn("[VATAuthAction] Auth disabled via feature switch — bypassing authorisation")
          block(AuthenticatedVATRequest(request, "local-test-user", requestedVRN, None, None))
        else if !requestedVRN.matches(vrnRegex) then
          logger.warn(s"VRN format invalid: $requestedVRN")
          Future.successful(
            BadRequest(Json.toJson(ErrorWrapper(correlationId, VrnFormatError)))
              .withHeaders("X-CorrelationId" -> correlationId.value)
          )
        else
          authorised(predicate(requestedVRN))
            .retrieve(
              affinityGroup and allEnrolments
                and internalId and externalId and agentCode and credentials
                and confidenceLevel and nino and saUtr and name and dateOfBirth
                and email and agentInformation and groupIdentifier and credentialRole
                and mdtpInformation and credentialStrength and loginTimes
                and itmpName and itmpDateOfBirth and itmpAddress
            ) {
              case affinityGroup ~ userEnrolments ~ Some(userId) ~ externalId ~ agentCode ~ credentials
                ~ confidenceLevel ~ nino ~ saUtr ~ name ~ dateOfBirth
                ~ email ~ agentInformation ~ groupIdentifier ~ credentialRole
                ~ mdtpInformation ~ credentialStrength ~ loginTimes
                ~ itmpName ~ itmpDateOfBirth ~ itmpAddress =>

                val maybeVrn = userEnrolments
                  .getEnrolment("HMRC-MTD-VAT")
                  .flatMap(_.getIdentifier("VRN"))
                  .map(_.value)

                val arn = userEnrolments
                  .getEnrolment("HMRC-AS-AGENT")
                  .flatMap(_.getIdentifier("AgentReferenceNumber"))
                  .map(_.value)

                val identityData = IdentityData(
                  internalId = Some(userId),
                  externalId = externalId,
                  agentCode = agentCode,
                  credentials = credentials,
                  confidenceLevel = confidenceLevel,
                  nino = nino,
                  saUtr = saUtr,
                  name = name,
                  dateOfBirth = dateOfBirth,
                  email = email,
                  agentInformation = agentInformation,
                  groupIdentifier = groupIdentifier,
                  credentialRole = credentialRole,
                  mdtpInformation = mdtpInformation,
                  itmpName = itmpName.getOrElse(ItmpName(None, None, None)),
                  itmpDateOfBirth = itmpDateOfBirth,
                  itmpAddress = itmpAddress.getOrElse(ItmpAddress(None, None, None, None, None, None, None, None)),
                  affinityGroup = affinityGroup,
                  credentialStrength = credentialStrength,
                  loginTimes = loginTimes
                )

                maybeVrn match
                  case None =>
                    logger.warn("User has no MTD VAT enrolment")
                    Future.successful(Forbidden("User has no MTD VAT enrolment"))

                  case Some(vrn) if vrn != requestedVRN =>
                    logger.warn(s"User VRN ($vrn) does not match requested VRN ($requestedVRN)")
                    Future.successful(Forbidden("User VRN does not match requested VRN"))

                  case Some(vrn) =>
                    block(AuthenticatedVATRequest(request, userId, vrn, arn, Some(identityData)))

              case _ =>
                logger.warn("Unable to retrieve required auth values")
                Future.successful(Unauthorized("Unable to retrieve required auth values"))
            }
            .recover { case e: AuthorisationException =>
              val error = s"Failed to authorise request $e"
              logger.warn(error)
              Unauthorized(error)
            }

  private def predicate(vrn: String): Predicate =
    Enrolment("HMRC-MTD-VAT")
      .withIdentifier("VRN", vrn)
      .withDelegatedAuthRule("mtd-vat-auth")
