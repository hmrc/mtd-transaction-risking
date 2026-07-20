package uk.gov.hmrc.mtdtransactionrisking.v1.models.response

import play.api.libs.json.Json
import uk.gov.hmrc.mtdtransactionrisking.support.UnitSpec
import uk.gov.hmrc.mtdtransactionrisking.v1.models.errors.TaxPeriodNotEndedError

class ObligationsResponseSpec extends UnitSpec:

  private val pastDetail = ObligationDetail(
    inboundCorrespondenceFromDate = "2000-01-01",
    inboundCorrespondenceToDate   = "2000-01-31",
    periodKey                     = "24AA"
  )

  private val futureDetail = ObligationDetail(
    inboundCorrespondenceFromDate = "2999-01-01",
    inboundCorrespondenceToDate   = "2999-01-31",
    periodKey                     = "24AB"
  )

  "ObligationsResponse" when:

    "written to JSON and read back" should:
      "round-trip correctly" in:
        val model = ObligationsResponse(
          obligations = Seq(
            Obligation(
              identification   = Some(Identification("AB123", "MTDBIS")),
              obligationDetails = Seq(pastDetail)
            )
          )
        )

        Json.toJson(model).as[ObligationsResponse] shouldBe model

    "finding an obligation period where the end date is in the past" should:
      "return Right(Some(ObligationPeriod))" in:
        val response = ObligationsResponse(
          obligations = Seq(Obligation(None, Seq(pastDetail)))
        )

        response.findOpenObligationPeriod("24AA") shouldBe
          Right(Some(ObligationPeriod("2000-01-01", "2000-01-31")))

    "finding an obligation period where the end date is not yet passed" should:
      "return Left(TaxPeriodNotEndedError)" in:
        val response = ObligationsResponse(
          obligations = Seq(Obligation(None, Seq(futureDetail)))
        )

        response.findOpenObligationPeriod("24AB") shouldBe Left(TaxPeriodNotEndedError)

    "finding an unknown period key" should:
      "return Right(None)" in:
        val response = ObligationsResponse(
          obligations = Seq(Obligation(None, Seq(pastDetail)))
        )

        response.findOpenObligationPeriod("ZZZZ") shouldBe Right(None)