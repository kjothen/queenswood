(ns com.repldriven.mono.bank-processors.system
  "Bare-require bundle for the combined processor services: every
  brick whose component-kinds any processor group may instantiate.
  Loaded by main.clj before `system/start`; nothing else lives here.
  Which processors a given service actually runs is decided entirely
  by its project's application.yml (ADR-0019) — registering a
  component-kind the YAML never wires is harmless."
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-bank.interface
    com.repldriven.mono.bank-bank-query.interface
    com.repldriven.mono.bank-cash-account.interface
    com.repldriven.mono.bank-cash-account-product.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.bank-interest.interface
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.bank-payee-check.interface
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.bank-transaction.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.event-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.identity-provider.interface
    com.repldriven.mono.keycloak.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.telemetry.interface))
