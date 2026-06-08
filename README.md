
# mtd-transaction-risking

The MTD Transactional Risking (TxR), publicly known as VAT Assist. It is a microservice designed to identify and flag potential risks or inaccuracies in VAT Returns before submission by showing users helpful messages.

The service acts as a pre-submission check within the normal VAT submission process. Since users submit VAT returns via accounting software (e.g. Sage, QuickBooks, Xero), software vendors are required to integrate VAT Assist into their products in order for users to see the relevant messages.

Running Tests
# Unit tests
sbt test

# Integration tests
sbt it/test

# All tests with coverage
sbt clean compile coverage test it/test coverageReport

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").