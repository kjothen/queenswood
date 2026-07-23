(ns com.repldriven.queenswood.api.system
  "Bare-require bundle for the bank API service: the components the
  unified banking HTTP surface needs wired into the system — the
  domain processors (bank, party, payment, transaction, balance,
  cash-account, cash-account-product, idv, policy, payee-check),
  identity (Keycloak, identity-provider), command and message
  infrastructure (command, command-processor, message-bus, pulsar),
  storage (fdb), serialization (avro, bank-schema), and the HTTP
  server and telemetry."
  (:require
    com.repldriven.queenswood.balance-query.interface
    com.repldriven.queenswood.bank.interface
    com.repldriven.queenswood.cash-account.interface
    com.repldriven.queenswood.cash-account-product.interface
    com.repldriven.queenswood.idv.interface
    com.repldriven.queenswood.party.interface
    com.repldriven.queenswood.payee-check.interface
    com.repldriven.queenswood.payment.interface
    com.repldriven.queenswood.policy.interface
    com.repldriven.queenswood.schema.interface
    com.repldriven.queenswood.transaction.interface

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
