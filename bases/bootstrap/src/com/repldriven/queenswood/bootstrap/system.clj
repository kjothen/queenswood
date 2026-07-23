(ns com.repldriven.queenswood.bootstrap.system
  "Bare-require bundle for the bootstrap (seeder): writes platform
  + micro Policy records into FDB and seeds the singleton internal
  Queenswood organization (with API key, internal Party, internal
  CashAccountProduct, and one CashAccount per currency). The
  migrator Job runs first and applies FDB record metadata + Pulsar
  topology; bootstrap consumes the persisted metadata read-only."
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.queenswood.bank.interface
    com.repldriven.queenswood.policy.interface
    com.repldriven.queenswood.schema.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.telemetry.interface))
