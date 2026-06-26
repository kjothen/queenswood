(ns com.repldriven.mono.bank-payee-check.interface
  "Confirmation of Payee (CoP) outcomes for a bank. Persists
  each payee check (request + match result) under a 24-hour TTL and
  exposes single-record reads plus paginated listing per bank."
  (:require
    [com.repldriven.mono.bank-payee-check.core :as core]
    com.repldriven.mono.bank-payee-check.system))

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
