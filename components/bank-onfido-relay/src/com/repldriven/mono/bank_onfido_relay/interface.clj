(ns com.repldriven.mono.bank-onfido-relay.interface
  "Transactional-outbox egress for the Onfido adapter. The webhook handler
  persists an `idv-completed` event with `save-event` (co-committed to the
  outbox changelog, relayed to the bus at-least-once), and the
  submit-idv-check consumer persists an outbound intent with `save-intent`
  (the out-of-transaction runner makes the Onfido create-applicant +
  create-check calls)."
  (:require
    [com.repldriven.mono.bank-onfido-relay.intent :as intent]
    [com.repldriven.mono.bank-onfido-relay.outbound :as outbound]
    [com.repldriven.mono.bank-onfido-relay.store :as store]
    com.repldriven.mono.bank-onfido-relay.system))

(defn save-event
  "Persist an `idv-completed` outbox event and append it to the changelog
  in one FDB transaction. Returns the event, or an `:onfido-outbox/save`
  anomaly (a uniqueness violation when the `dedup-key` was already
  recorded — a redelivered webhook).

  Args:
  - txn: an open FDB transaction or `{:record-db :record-store}` config.
  - event: a map with `:outbox-id`, `:dedup-key`, `:event-name`,
    `:payload` (Avro bytes), `:correlation-id`, `:causation-id`,
    `:created-at`."
  [txn event]
  (store/save-event txn event))

(defn save-intent
  "Persist a pending submit-idv-check intent — the consume-side outbox
  write — in one FDB transaction. The out-of-transaction runner makes the
  Onfido calls. Returns the intent, or an `:onfido-outbound/save` anomaly
  (a uniqueness violation when the `dedup-key` was already enqueued).

  Args:
  - txn: an open FDB transaction or `{:record-db :record-store}` config.
  - intent: a map with `:intent-id`, `:dedup-key` (verification-id),
    `:request` (EDN-encoded command data), `:status` (\"pending\"),
    `:attempts`, `:created-at`."
  [txn intent]
  (intent/save-intent txn intent))

(defn uniqueness-violation?
  "True if a `save-event`/`save-intent` result is a duplicate-`dedup-key`
  violation (already recorded — safe to treat as accepted)."
  [result]
  (store/uniqueness-violation? result))

(defn parse-external-id
  "Unpack Onfido's composite `external_id` into
  `{:bank-id ... :verification-id ...}`, or nil."
  [external-id]
  (outbound/parse-external-id external-id))
