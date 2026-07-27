(ns com.repldriven.queenswood.scheduler-processor.system
  "Bare-require bundle of every brick whose component-kinds the
  scheduler service needs registered. Loaded by main.clj before
  `system/start`; nothing else lives here."
  (:require
    com.repldriven.queenswood.interest.interface
    com.repldriven.queenswood.scheduler.interface
    com.repldriven.queenswood.schema.interface
    com.repldriven.queenswood.fdb.interface
    com.repldriven.mono.scheduler.interface
    com.repldriven.mono.telemetry.interface))
