(ns com.repldriven.mono.bank-api.system
  "Bare-require bundle for the bank API service: the components the
  unified banking HTTP surface needs wired into the system — the
  domain processors (bank, party, payment, transaction, balance,
  cash-account, cash-account-product, idv, policy, payee-check),
  identity (Keycloak, identity-provider), command and message
  infrastructure (command, command-processor, message-bus, pulsar),
  storage (fdb), serialization (avro, bank-schema), and the HTTP
  server and telemetry."
  (:require
    com.repldriven.mono.bank-balance.interface
    com.repldriven.mono.bank-bank.interface
    com.repldriven.mono.bank-cash-account.interface
    com.repldriven.mono.bank-cash-account-product.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.bank-payee-check.interface
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.bank-policy.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.bank-transaction.interface

    com.repldriven.mono.avro.interface
    com.repldriven.mono.command.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.identity-provider.interface
    com.repldriven.mono.keycloak.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.telemetry.interface))
