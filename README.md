# mtd-transaction-risking

MTD Transaction Risking (TxR), publicly known as **VAT Assist**, is an HMRC microservice that
analyses VAT return data and returns feedback messages highlighting potential mistakes or
discrepancies before a VAT return is formally submitted.

The service acts as a pre-submission check within the MTD VAT submission journey. Because users
submit VAT returns via third-party accounting software (such as Sage, QuickBooks, and Xero),
software vendors are required to integrate VAT Assist into their products so that users receive
relevant feedback before submission.

## Keywords

- **TxR**: MTD Transaction Risking, the internal name for this service
- **VAT Assist**: the public-facing name for the service as seen by vendors and end users
- **VRN**: VAT Registration Number, a nine-digit identifier for a VAT-registered business
- **periodKey**: a four-character alphanumeric string identifying the VAT return period,
  obtained from the VAT obligations endpoint on the VAT (MTD) API after a generate report request is validated.
- **RDS**: the SAS risking decision service that generates the feedback report and records
  acknowledgements
- **RSD**: the interactions datastore where each report and acknowledgement is recorded for
  case workers
- **NRS** — Non-Repudiation Service where events are submitted via the NRS orchestrator
- **Feedback stub** — the VAT Assist stub that returns `reportId`, `correlationId`,
  and English and Welsh feedback messages called in external test when configured
- **Gov-Test-Scenario** — an HTTP request header used in external test environments to select
  which canned scenario the stub returns; forwarded downstream by this service via
  `environmentHeaders` configuration
- **feedbackStubBaseUrl** — an optional config value present only in external test
  environments its presence determines whether the service calls the feedback stub or
  the real downstream services

## How it works

### Feedback (`POST /feedback/:vrn`)

1. Authorise the caller (MTD VAT enrolment, or agent with ARN).
2. Validate the VAT return with **vat-api** and resolve the obligation for the `periodKey`.
3. Fetch fraud-risk insights from **insights-proxy**.
4. Obtain an RDS bearer token from SAS (cached until shortly before expiry) and call
   **RDS generate**.
5. Return the feedback to the vendor.
6. Asynchronously (does not affect the response):
    - store the interaction in **RSD**
    - if the report contains actual feedback (any item other than `0`) and NRS is enabled,
      submit two NRS events: `vata-request-feedback` (the vendor's original request body) and
      `vata-generate-report` (the feedback as returned to the vendor)

### Acknowledge (`POST /acknowledge/:vrn/:reportId/:correlationId`)

1. Authorise the caller and validate the path and query parameters.
2. Call **RDS acknowledge**.
3. On acceptance return `204`, then asynchronously store the interaction in **RSD** and, if
   NRS is enabled, submit the `vata-acknowledge-report` NRS event.

### NRS retries

When an NRS submission fails with a retryable error (429, 499, 5xx, timeout), it is stored as
an encrypted work item in MongoDB and retried by a scheduler with exponential backoff and
jitter. Permanent failures are retained for investigation. Retries are controlled by the
`nrs-retry` feature switch and configuration block.

## Technical documentation

This is a Scala 3 Play Framework microservice, following standard HMRC microservice conventions.
It runs on port **9858** by default.

### Running the app

MongoDB must be running locally (used for NRS retry work items).

```bash
sbt run
```

The service will start on `http://localhost:9858`.

Verify it is running:

```bash
curl http://localhost:9858/ping/ping
```

Downstream services default to `localhost:9859`, which is where
[mtd-transaction-risking-stub](https://github.com/hmrc/mtd-transaction-risking-stub) runs.

### Feature switches

Feature switches are configured in `application.conf` under the `feature-switch` block:

| Switch | Default | Description |
|---|---|---|
| `version-1.enabled` | `true` | Enables v1 routing via the `Accept` header |
| `auth.enabled` | `true` | Enables MTD VAT enrolment authentication. Set to `false` locally to bypass auth |
| `nrs-submission.enabled` | `false` | Submits NRS notable events for feedback and acknowledgements |
| `nrs-retry.enabled` | `false` | Runs the scheduler that retries failed NRS submissions |

To bypass auth locally, set `auth.enabled = false` in `application.conf`.

When either NRS switch is enabled, `nrs-retry.crypto.key` must be configured (it encrypts the
stored submissions). The service fails at startup if it is missing. Each environment should set
both switches explicitly in its app-config, and only environments that use NRS should hold the
key and NRS configuration.

### Endpoints

#### Common headers

| Header | Value |
|---|---|
| `Accept` | `application/vnd.hmrc.1.0+json` |
| `Content-Type` | `application/json` |
| `Authorization` | `Bearer <token>` |

Every response includes `X-CorrelationId`, a UUID for tracing the request through the logs.

#### `POST /feedback/:vrn`

Requests VAT Assist feedback for a given VRN. The request body must contain the nine VAT
return fields corresponding to boxes 1–9 on a paper VAT return.

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

**Response (200, with feedback):**

```json
{
  "reportId": "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c",
  "correlationId": "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D",
  "englishFeedback": [
    {
      "itemNumber": "1",
      "title": "VAT Return Query",
      "body": "Please review your VAT return figures.",
      "action": "Check your sales records.",
      "links": [
        { "title": "VAT guidance", "url": "https://www.gov.uk/vat-returns" }
      ],
      "path": "vatDueSales"
    }
  ],
  "welshFeedback": [
    {
      "itemNumber": "1",
      "title": "Ymholiad Ffurflen TAW",
      "body": "Adolygwch eich ffigurau.",
      "action": "Gwiriwch eich cofnodion.",
      "links": [
        { "title": "Canllawiau TAW", "url": "https://www.gov.uk/ffurflenni-taw" }
      ],
      "path": "vatDueSales"
    }
  ]
}
```

**Response (200, no feedback):** RDS returns a single item `0` message in each language.
`path` is omitted for item `0`, and no NRS events are submitted.

```json
{
  "reportId": "f2fb30e5-4ab6-4a29-b3c1-c7264259ff1c",
  "correlationId": "E9F65715BBC9222477B27074804BBDD5C73CDE62F84D8B00CFD05B883534AF3D",
  "englishFeedback": [
    {
      "itemNumber": "0",
      "title": "HMRC feedback: HMRC Assist has not returned any messages.",
      "body": "This is not a complete check of your return or confirmation that your return is accurate. Your return has not been submitted at this stage.",
      "action": "Consider any further checks before finalising your return.",
      "links": [
        { "title": "VAT guidance", "url": "https://www.gov.uk/vat-returns" }
      ]
    }
  ],
  "welshFeedback": [ "..." ]
}
```

**Notes:**

| Field | Notes                                                                                                              |
|---|--------------------------------------------------------------------------------------------------------------------|
| `reportId` | The RDS `feedbackId`; pass it to the acknowledge endpoint                                                          |
| `correlationId` | The RDS correlation id, 64 hex characters; pass it to the acknowledge endpoint. Omitted if RDS does not return one |
                                              |

#### `POST /acknowledge/:vrn/:reportId/:correlationId?presentedDateTime=...`

Records that the feedback for a report was presented to the user. No request body.

| Parameter | In | Format |
|---|---|---|
| `vrn` | path | `^\d{9}$` |
| `reportId` | path | UUID, from the feedback response |
| `correlationId` | path | 64 hex characters, from the feedback response |
| `presentedDateTime` | query | ISO-8601 instant, e.g. `2026-10-12T14:30:00Z` |

**Responses:**

| Status | Code | When |
|---|---|---|
| `204` | | RDS accepted the acknowledgement |
| `400` | `REPORT_ID_INVALID` | `reportId` is not a UUID |
| `400` | `CORRELATION_ID_INVALID` | `correlationId` is not 64 hex characters |
| `400` | `PRESENTED_DATE_TIME_INVALID` | `presentedDateTime` is missing or not ISO-8601 |
| `500` | `ACKNOWLEDGEMENT_VALIDATION_FAILED` | RDS rejected the acknowledgement (no matching report) |
| `500` | `INTERNAL_SERVER_ERROR` | RDS returned an unexpected or malformed response |
| `503` | | RDS is unavailable |

Validation errors are returned before any downstream call is made.


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
  Stub for local and external test environments (feedback, insights, RDS, RSD and NRS)

## Licence

This code is open source software licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).