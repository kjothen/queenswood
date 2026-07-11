(ns com.repldriven.mono.bank-operational-processors.system
  "Bare-require bundle for the operational processors service —
  bank, party, cash-account, cash-account-product, idv — every
  brick whose component-kinds its application.yml instantiates.
  Loaded by main.clj before `system/start`; nothing else lives
  here. The service's composition is the project's application.yml
  (ADR-0019)."
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-bank.interface
    com.repldriven.mono.bank-cash-account.interface
    com.repldriven.mono.bank-cash-account-product.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.event-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.identity-provider.interface
    com.repldriven.mono.keycloak.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.telemetry.interface))
