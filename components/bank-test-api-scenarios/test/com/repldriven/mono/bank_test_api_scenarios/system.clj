(ns com.repldriven.mono.bank-test-api-scenarios.system
  "Bare-require bundle for the api-scenarios test runner. Each
  `defcomponents` registration only fires when its namespace loads,
  so the system config in `application-test.yml` references brick
  component-kinds that need their `system.clj` (or `interface.clj`)
  loaded somewhere on the test classpath. Bundling them here means
  the test namespace only needs a single bare require of this ns."
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-cash-account.interface
    com.repldriven.mono.bank-cash-account.system
    com.repldriven.mono.bank-clearbank-adapter.system
    com.repldriven.mono.bank-clearbank-simulator.system
    com.repldriven.mono.bank-clearbank-webhook.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.bank-idv.system
    com.repldriven.mono.bank-interest.interface
    com.repldriven.mono.bank-onfido-adapter.system
    com.repldriven.mono.bank-onfido-simulator.system
    com.repldriven.mono.bank-bank.interface
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.bank-party.system
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.bank-policy.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.bank-transaction.interface

    com.repldriven.mono.command.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.event-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.identity-provider.interface
    com.repldriven.mono.keycloak.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.testcontainers.interface))
