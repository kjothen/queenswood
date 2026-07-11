(ns com.repldriven.mono.bank-financial-processors.system
  "Bare-require bundle for the financial processors service —
  payment, transaction, interest, payee-check — every brick whose
  component-kinds its application.yml instantiates. Loaded by
  main.clj before `system/start`; nothing else lives here. The
  service's composition is the project's application.yml
  (ADR-0019)."
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-interest.interface
    com.repldriven.mono.bank-payee-check.interface
    com.repldriven.mono.bank-payment.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.bank-transaction.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.event-processor.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.telemetry.interface))
