(ns com.repldriven.mono.bank-payee-check.interface
  "Confirmation of Payee (CoP) outcomes for a bank. Persists
  each payee check (request + match result) under a 24-hour TTL and
  exposes single-record reads plus paginated listing per bank."
  (:require
    [com.repldriven.mono.bank-payee-check.core :as core]))

(defn check-payee
  "Persist a payee check for a bank and return the saved
  check map or anomaly. Generates the check id and timestamps;
  the result map is sanitised against the CoP shape.

  Args:
  - config: bank map providing the FDB seam.
  - bank-id: bank owning the check.
  - request: the inbound CoP request payload.
  - result: the CoP match outcome map."
  [config bank-id request result]
  (core/check-payee config bank-id request result))

(defn get-check
  "Load a payee check by composite primary key. Returns the check
  map or a `:payee-check/not-found` rejection anomaly.

  Args:
  - txn: an open FDB transaction or system bank map.
  - bank-id: bank owning the check.
  - check-id: payee-check identifier (`chk.<ulid>`)."
  [txn bank-id check-id]
  (core/get-check txn bank-id check-id))

(defn get-checks
  "List payee checks for a bank with pagination. Returns
  `{:items [maps] :before id|nil :after id|nil}` or anomaly.
  Defaults to newest-first, limit 20.

  Args:
  - txn: an open FDB transaction or system bank map.
  - bank-id: bank to list against.
  - opts (optional): `:after`, `:before`, `:limit`, `:order`
    (`:asc` or `:desc`, default `:desc`)."
  ([txn bank-id]
   (core/get-checks txn bank-id))
  ([txn bank-id opts]
   (core/get-checks txn bank-id opts)))
