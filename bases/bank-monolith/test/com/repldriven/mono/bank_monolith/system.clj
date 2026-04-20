(ns com.repldriven.mono.bank-monolith.system
  (:require
    com.repldriven.mono.bank-cash-account.interface
    com.repldriven.mono.bank-clearbank-adapter.system
    com.repldriven.mono.bank-clearbank-simulator.system
    com.repldriven.mono.bank-clearbank-webhook.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.bank-interest.interface
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.bank-transaction.interface

    com.repldriven.mono.command.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.event-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.testcontainers.interface))
