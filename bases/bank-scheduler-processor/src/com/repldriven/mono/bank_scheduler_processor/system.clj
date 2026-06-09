(ns com.repldriven.mono.bank-scheduler-processor.system
  "Bare-require bundle of every brick whose component-kinds the
  scheduler service needs registered. Loaded by main.clj before
  `system/start`; nothing else lives here."
  (:require
    com.repldriven.mono.bank-interest.interface
    com.repldriven.mono.bank-scheduler.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.scheduler.interface
    com.repldriven.mono.telemetry.interface))
