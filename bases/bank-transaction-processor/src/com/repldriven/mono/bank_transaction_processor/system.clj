(ns com.repldriven.mono.bank-transaction-processor.system
  "Bare-require bundle of every brick whose component-kinds the
  processor needs registered. Loaded by main.clj before
  `system/start`; nothing else lives here."
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.bank-transaction.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.telemetry.interface))
