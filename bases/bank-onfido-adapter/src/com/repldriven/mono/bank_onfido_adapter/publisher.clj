(ns com.repldriven.mono.bank-onfido-adapter.publisher
  "Maps an inbound `check.completed` webhook payload to an
  `idv-completed` bus-event descriptor `{:event-name :dedup-key :data}`.
  The Onfido `result` (`clear` | `consider`) maps to a bank-idv status;
  the `external_id` carries `:bank-id` and `:verification-id`. The webhook
  handler persists the descriptor to the outbox; the relay publishes it."
  (:require
    [com.repldriven.mono.bank-onfido-relay.interface :as relay]))

(defn- result->status
  [result]
  (case result
    "clear" "ACCEPTED"
    "consider" "REJECTED"))

(defn ->idv-completed
  "Build the idv-completed event descriptor from a check.completed
  payload's `:object`. `dedup-key` is the verification identity, so a
  redelivered webhook does not double-enqueue."
  [payload]
  (let [{:keys [object]} payload
        {:keys [external_id result]} object
        {:keys [bank-id verification-id]} (relay/parse-external-id external_id)]
    {:event-name "idv-completed"
     :dedup-key (str verification-id ":completed")
     :data {:bank-id bank-id
            :verification-id verification-id
            :status (result->status result)}}))
