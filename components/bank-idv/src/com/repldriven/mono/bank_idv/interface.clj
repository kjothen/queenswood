(ns com.repldriven.mono.bank-idv.interface
  "Identity verification (IDV) records and lifecycle. Persists IDV
  state per (organization, verification-id) and bridges to an
  IDV-provider adapter via the message bus: a `submit-idv-check`
  command is published on initiation, and an `idv-completed` event
  flips the record to accepted or rejected. Registers IDV component
  kinds (processor, event-processor, party-watcher-handler) through
  this brick's `system` namespace."
  (:require
    com.repldriven.mono.bank-idv.system

    [com.repldriven.mono.bank-idv.store :as store]))

(defn get-idv
  "Load an IDV by composite primary key. Returns the IDV map or
  an `:idv/not-found` rejection anomaly if the record is missing.

  Args:
  - txn: an open FDB transaction or system bank map.
  - organization-id: organization owning the IDV.
  - verification-id: IDV identifier (`idv.<ulid>`)."
  [txn organization-id verification-id]
  (store/get-idv txn organization-id verification-id))
