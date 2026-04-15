(ns com.repldriven.mono.bank-cash-account.commands
  (:require
    [com.repldriven.mono.bank-cash-account.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- payment-address->avro
  "Flattens protojure oneof :identifier wrapper to flat
  Avro-compatible shape."
  [{:keys [scheme identifier]}]
  {:scheme scheme
   :scan (:scan identifier)
   :value (:value identifier)})

(defn- account->avro
  "Reshapes a CashAccount map for Avro serialization."
  [account]
  (update account
          :payment-addresses
          (fn [addresses] (mapv payment-address->avro addresses))))

(defn- ->response
  "Converts an account map to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "cash-account")
                                (account->avro result))})))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :cash-account/process-command
                  {:message "No schema found for command"
                   :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "open-cash-account"
          (->response config (core/open-account config data))

          "close-cash-account"
          (->response config (core/close-account config data))

          "get-cash-account"
          (let [{:keys [organization-id account-id]} data]
            (->response config
                        (core/get-account config organization-id account-id)))

          (error/reject :cash-account/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord CashAccountProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
