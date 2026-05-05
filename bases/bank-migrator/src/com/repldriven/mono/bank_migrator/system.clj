(ns com.repldriven.mono.bank-migrator.system
  "Bare-require bundle for the migrator: applies FDB record metadata
  (via `fdb/meta-store` with `migrate: true`, gated on FDB_MIGRATE)
  and declares the Pulsar topology (tenants/namespaces/topics/schemas).
  No domain seeding — the bank-bootstrap-service Job runs after this
  one and handles the internal-organization seed + policy records."
  (:require
    com.repldriven.mono.avro.interface
    com.repldriven.mono.bank-schema.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.telemetry.interface))
