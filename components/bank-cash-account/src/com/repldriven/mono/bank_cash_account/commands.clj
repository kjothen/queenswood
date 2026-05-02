(ns com.repldriven.mono.bank-cash-account.commands
  (:require
    [com.repldriven.mono.bank-cash-account.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- ->response
  "Converts an account map to an ACCEPTED response. Returns anomalies
  unchanged for the processor to handle. The account's
  `:payment-addresses` shape — `{:scheme :scan :value}` — matches the
  Avro schema directly since the proto `PaymentAddress` dropped its
  `oneof identifier` wrapper, so no reshaping is needed here."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "cash-account") result)})))

(def ^:private command-handlers
  "Map of command-name → `(fn [config data] response)`. Lifting the
  registry out of the dispatcher's `case` lets unknown-command
  rejection fire BEFORE schema lookup or Avro deserialization, so
  a pure unit test can exercise the rejection path without
  spinning up the system."
  {"open-cash-account" (fn [config data]
                         (->response config (core/open-account config data)))
   "close-cash-account" (fn [config data]
                          (->response config (core/close-account config data)))
   "get-cash-account"
   (fn [config data]
     (let [{:keys [organization-id account-id]} data]
       (->response config
                   (core/get-account config organization-id account-id))))})

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (error/reject :cash-account/unknown-command
                    (str "Unknown command: " command))
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (error/fail :cash-account/process-command
                      {:message "No schema found for command"
                       :command command})
          (let-nom> [data (avro/deserialize-same schema payload)]
            (handler config data)))))))

(defrecord CashAccountProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
