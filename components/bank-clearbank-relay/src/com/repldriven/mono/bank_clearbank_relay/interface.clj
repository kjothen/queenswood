(ns com.repldriven.mono.bank-clearbank-relay.interface
  "Transactional-outbox egress for the ClearBank adapter. Webhook
  handlers persist an event with `save-event` — co-committed to the
  outbox store's changelog — and a changelog relay watcher (the
  `clearbank-relay/relay-handler` component-kind) publishes it to the
  message bus at-least-once. This decouples 'webhook received' from
  'downstream told' so they cannot diverge."
  (:require
    [com.repldriven.mono.bank-clearbank-relay.store :as store]
    com.repldriven.mono.bank-clearbank-relay.system))

(defn save-event
  "Persist an outbox event and append it to the changelog in one FDB
  transaction. Returns the event, or a `:clearbank-outbox/save` anomaly
  (a uniqueness violation when the `dedup-key` was already recorded — a
  redelivered webhook).

  Args:
  - txn: an open FDB transaction or `{:record-db :record-store}` config.
  - event: a map with `:outbox-id`, `:dedup-key`, `:event-name`,
    `:payload` (Avro-serialised bytes), `:correlation-id`,
    `:causation-id`, `:created-at`."
  [txn event]
  (store/save-event txn event))

(defn uniqueness-violation?
  "True if a `save-event` result is a duplicate-`dedup-key` violation,
  i.e. the webhook has already been recorded and can be acked 200
  without re-enqueuing."
  [result]
  (store/uniqueness-violation? result))
