(ns com.repldriven.mono.bank-idv-onfido-adapter.publisher
  "Transforms an inbound `check.completed` webhook payload from
  Onfido into an internal `idv-completed` event on the message
  bus. The Onfido `result` field (`clear` | `consider`) maps to a
  bank-idv status string the brick's event-processor reacts on."
  (:require
    [com.repldriven.mono.bank-idv-onfido-adapter.events :as events]
    [com.repldriven.mono.bank-idv-onfido-adapter.onfido :as onfido]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- result->status
  "Onfido `clear` means the check passed; `consider` means it
  was flagged for review and bank-idv treats the verification as
  rejected (we don't model `:idv-status-in-review` yet)."
  [result]
  (case result
    "clear" "ACCEPTED"
    "consider" "REJECTED"))

(defn publish-idv-completed
  "Reads the `:check.completed` payload's `:object` and publishes
  an `idv-completed` event keyed by the smuggled `:external_id`
  (a composite of `:organization-id` and `:verification-id`)."
  [config payload]
  (let [{:keys [bus avro event-channel]} config
        {:keys [object]} payload
        {:keys [external_id result]} object
        {:keys [organization-id verification-id]}
        (onfido/parse-external-id external_id)]
    (error/try-nom
     :idv-onfido-adapter/publish-idv-completed
     "Failed to publish idv-completed event"
     (events/publish bus
                     avro
                     "idv-completed"
                     (str (utility/uuidv7))
                     (str (utility/uuidv7))
                     {:organization-id organization-id
                      :verification-id verification-id
                      :status (result->status result)}
                     {:event-channel event-channel}))))
