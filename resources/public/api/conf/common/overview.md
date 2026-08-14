This API allows your software to:
* send draft return data and request feedback from HMRC, without formally submitting the return
* acknowledge any feedback, and confirm that it has been presented verbatim to the user

Before using the API to request HMRC Assist feedback for VAT, you must call the [obligations endpoint](/api-documentation/docs/api/service/vat-api/1.0/oas/page#tag/organisations/operation/RetrieveVATobligations) on the VAT (MTD) API to identify the VAT period (by retrieving the period key).

For further information about HMRC Assist for VAT, and how to connect to the VAT MTD APIs, see the [VAT MTD end-to-end service guide](/guides/vat-mtd-end-to-end-service-guide/).
