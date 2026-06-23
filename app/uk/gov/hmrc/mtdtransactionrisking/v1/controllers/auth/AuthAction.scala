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

import play.api.mvc.*
import play.api.mvc.Results.{Forbidden, Unauthorized}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mtdtransactionrisking.utils.Logging
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


case class AuthenticatedVATRequest[A](request: Request[A],
                                      internalId: String,
                                      vrn: String) extends WrappedRequest[A](request)

class VATAuthAction @Inject()(override val authConnector: AuthConnector,
                              bodyParser: BodyParsers.Default)(using ec: ExecutionContext)
  extends AuthorisedFunctions with Logging:

  private def predicate(vrn: String): Predicate =
    Enrolment("HMRC-MTD-VAT")
      .withIdentifier("VRN", vrn)
      .withDelegatedAuthRule("mtd-vat-auth")

  def authorisedFor(requestedVRN: String): ActionBuilder[AuthenticatedVATRequest, AnyContent] =
    new ActionBuilder[AuthenticatedVATRequest, AnyContent]:

      override protected def executionContext: ExecutionContext = ec

      override def parser: BodyParser[AnyContent] = bodyParser

      override def invokeBlock[A](request: Request[A],
                                  block: AuthenticatedVATRequest[A] => Future[Result]): Future[Result] =
        given hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        authorised(predicate(requestedVRN))
          .retrieve(internalId and allEnrolments) {
            case Some(userId) ~ userEnrolments =>
              val maybeVrn = userEnrolments
                .getEnrolment("HMRC-MTD-VAT")
                .flatMap(_.getIdentifier("VRN"))
                .map(_.value)

              maybeVrn match
                case None =>
                  logger.warn(s"User has no MTD VAT enrolment")
                  Future.successful(Forbidden("User has no MTD VAT enrolment"))

                case Some(vrn) if vrn != requestedVRN =>
                  logger.warn(s"User VRN ($vrn) does not match requested VRN ($requestedVRN)")
                  Future.successful(Forbidden(s"User VRN does not match requested VRN"))

                case Some(vrn) =>
                  block(AuthenticatedVATRequest(request, userId, vrn))

            case _ =>
              logger.warn("Unable to retrieve required auth values")
              Future.successful(Unauthorized("Unable to retrieve required auth values"))
          }
          .recover {
            case e: AuthorisationException =>
              val error = s"Failed to authorise request $e"
              logger.warn(error)
              Unauthorized(error)
          }
