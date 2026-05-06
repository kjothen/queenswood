(ns com.repldriven.mono.bank-payee-check.interface
  "Confirmation of Payee (CoP) outcomes for an organization. Persists
  each payee check (request + match result) under a 24-hour TTL and
  exposes single-record reads plus paginated listing per
  organization."
  (:require
    [com.repldriven.mono.bank-payee-check.core :as core]))

(defn check-payee
  "Persist a payee check for an organization and return the saved
  check map or anomaly. Generates the check id and timestamps;
  the result map is sanitised against the CoP shape.

  Args:
  - config: bank map providing the FDB seam.
  - organization-id: organization owning the check.
  - request: the inbound CoP request payload.
  - result: the CoP match outcome map."
  [config organization-id request result]
  (core/check-payee config organization-id request result))

(defn get-check
  "Load a payee check by composite primary key. Returns the check
  map or a `:payee-check/not-found` rejection anomaly.

  Args:
  - txn: an open FDB transaction or system bank map.
  - org-id: organization owning the check.
  - check-id: payee-check identifier (`chk.<ulid>`)."
  [txn org-id check-id]
  (core/get-check txn org-id check-id))

(defn get-checks
  "List payee checks for an organization with pagination. Returns
  `{:items [maps] :before id|nil :after id|nil}` or anomaly.
  Defaults to newest-first, limit 20.

  Args:
  - txn: an open FDB transaction or system bank map.
  - org-id: organization to list against.
  - opts (optional): `:after`, `:before`, `:limit`, `:order`
    (`:asc` or `:desc`, default `:desc`)."
  ([txn org-id]
   (core/get-checks txn org-id))
  ([txn org-id opts]
   (core/get-checks txn org-id opts)))
