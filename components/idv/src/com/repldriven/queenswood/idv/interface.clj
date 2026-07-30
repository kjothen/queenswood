(ns com.repldriven.queenswood.idv.interface
  "Identity verification (IDV) records and lifecycle. Persists IDV
  state per (organization, verification-id) and bridges to an
  IDV-provider adapter via the message bus: a `submit-idv-check`
  command is published on initiation, and an `idv-completed` event
  flips the record to in-review, accepted, rejected, or failed.
  In-review and failed are non-terminal — accepted/rejected may
  still follow once manual review resolves, or the provider retries
  after a technical failure. The owning party stays pending
  throughout; the IDV record is the source of truth for why.
  Registers IDV component kinds (processor, event-processor,
  party-event-processor) through this brick's `system` namespace."
  (:require
    [com.repldriven.queenswood.idv.system]

    [com.repldriven.queenswood.idv.core :as core]))

(defn get-idv
  "Load an IDV by composite primary key. Returns the IDV map or
  an `:idv/not-found` rejection anomaly if the record is missing.

  Args:
  - txn: an open FDB transaction or system bank map.
  - bank-id: bank owning the IDV.
  - verification-id: IDV identifier (`idv.<ulid>`)."
  [txn bank-id verification-id]
  (core/get-idv txn bank-id verification-id))
