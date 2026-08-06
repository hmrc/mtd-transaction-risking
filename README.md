# mtd-transaction-risking

MTD Transaction Risking (TxR), publicly known as **VAT Assist**, is an HMRC microservice that
analyses VAT return data and returns feedback messages highlighting potential mistakes or
discrepancies before a VAT return is formally submitted.

The service acts as a pre-submission check within the MTD VAT submission journey. Because users
submit VAT returns via third-party accounting software (such as Sage, QuickBooks, and Xero),
software vendors are required to integrate VAT Assist into their products so that users receive
relevant feedback before submission.

## Nomenclature

- **TxR** — MTD Transaction Risking, the internal name for this service
- **VAT Assist** — the public-facing name for the service as seen by vendors and end users
- **VRN** — VAT Registration Number, a nine-digit identifier for a VAT-registered business
- **periodKey** — a four-character alphanumeric string identifying the VAT return period
- **Insights proxy** — the CIP (Customer Insight Platform) risk service that returns a risk
  score and correlation ID for a given VRN; called in production
- **Feedback stub** — the VAT Assist stub that returns `reportId`, `correlationId`,
  and English and Welsh feedback messages; called in external test when configured
- **Gov-Test-Scenario** — an HTTP request header used in external test environments to select
  which canned scenario the stub returns; forwarded downstream by this service via
  `environmentHeaders` configuration
- **feedbackStubBaseUrl** — an optional config value present only in external test
  environments; its presence determines whether the service calls the feedback service or
  the insights proxy

## Technical documentation

This is a Scala 3 Play Framework microservice, following standard HMRC microservice conventions.
It runs on port **9858** by default.

### Running the app

```bash
sbt run
```

The service will start on `http://localhost:9858`.

Verify it is running:

```bash
curl http://localhost:9858/ping/ping
```

### Feature switches

Feature switches are configured in `application.conf` under the `feature-switch` block:

| Switch | Default | Description |
|---|---|---|
| `version-1.enabled` | `true` | Enables v1 routing via the `Accept` header |
| `auth.enabled` | `true` | Enables MTD VAT enrolment authentication. Set to `false` locally to bypass auth |

To bypass auth locally, set `auth.enabled = false` in `application.conf`.

### Endpoints

#### `POST /assist/:vrn`

Requests VAT Assist feedback for a given VRN. The request body must contain the nine VAT
return fields corresponding to boxes 1–9 on a paper VAT return.

**Required headers:**

| Header | Value |
|---|---|
| `Accept` | `application/vnd.hmrc.1.0+json` |
| `Content-Type` | `application/json` |
| `Authorization` | `Bearer <token>` |

**Path parameter:**

| Parameter | Format | Description |
|---|---|---|
| `vrn` | `^\d{9}$` | VAT Registration Number |

**Request body:**

```json
{
  "periodKey": "18AD",
  "vatDueSales": 100.00,
  "vatDueAcquisitions": 100.00,
  "totalVatDue": 200.00,
  "vatReclaimedCurrPeriod": 100.00,
  "netVatDue": 100.00,
  "totalValueSalesExVAT": 1000,
  "totalValuePurchasesExVAT": 1000,
  "totalValueGoodsSuppliedExVAT": 1000,
  "totalAcquisitionsExVAT": 1000
}
```

**Response (200 — external test, feedback path):**

```json
{
  "reportId": "f2fb30e5-4ab6-4a29-b3c1-c00000000001",
  "correlationId": "c75f40a6-a3df-4429-a697-471eeec46435",
  "englishFeedback": [
    {
      "itemNumber": "001",
      "title": "VAT due on sales appears low",
      "body": "The VAT due on sales figure appears lower than expected.",
      "action": "Please review box 1 and box 6 of your VAT return.",
      "path": "/guidance/vat-returns"
    }
  ],
  "welshFeedback": [
    {
      "itemNumber": "001",
      "title": "Mae'r TAW sy'n ddyledus ar werthiannau'n ymddangos yn isel",
      "body": "Mae'r ffigwr TAW sy'n ddyledus ar werthiannau'n ymddangos yn is na'r disgwyl.",
      "action": "Adolygwch flwch 1 a blwch 6 o'ch ffurflen TAW.",
      "path": "/guidance/vat-returns"
    }
  ]
}
```

**Response header:**

| Header | Description |
|---|---|
| `X-CorrelationId` | Unique 36-character UUID for operation tracking |


### Configuration

Key configuration in `application.conf`:

```hocon
microservice.services {
  insights-proxy {
    host              = localhost
    port              = 9859
    submit-url        = "/check/insights"
  }

  # Only present in external test — absence means production insights path is used
  feedback-stub {
    host              = localhost
    port              = 9859
    submit-url        = "/feedback"
    environmentHeaders = ["Gov-Test-Scenario", "Accept"]
  }
}

bootstrap.http.headersAllowlist = ["Gov-Test-Scenario", "Accept"]
```

### Running the test suite

```bash
# Unit tests
sbt test

# Integration tests
sbt it/test

# All tests with coverage report
sbt clean compile coverage test it/test coverageReport
```

The coverage report is written to
`target/scala-3.3.6/scoverage-report/index.html`.

## Related services

- [vat-api](https://github.com/hmrc/vat-api) — VAT return submission and validation
- [mtd-transaction-risking-stub](https://github.com/hmrc/mtd-transaction-risking-stub) —
  Stub for local and external test environments

## Licence

This code is open source software licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).