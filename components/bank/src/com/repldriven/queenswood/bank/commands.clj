(ns com.repldriven.queenswood.bank.commands
  (:require
    [com.repldriven.queenswood.bank.core :as core]

    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config
          {:keys [bank membership]} result]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "bank")
                                (assoc bank :membership membership))})))

(defn- create-bank
  [config data]
  (let [{:keys [name status tier currencies audience company-binding
                membership]}
        data]
    (->response config
                (core/new-bank config
                               name
                               status
                               tier
                               currencies
                               {:identity-provider (:identity-provider config)
                                :audience audience
                                :company-binding company-binding
                                :membership membership}))))

(defn- change-bank-tier
  [config data]
  (let [{:keys [bank-id tier]} data
        result (core/change-tier config bank-id tier)]
    (if (error/anomaly? result)
      result
      (->response config {:bank result}))))

(defn- change-bank-status
  [config data]
  (let [{:keys [bank-id status audience]} data
        result (core/change-status config
                                   bank-id
                                   status
                                   {:identity-provider (:identity-provider
                                                        config)
                                    :audience audience})]
    (if (error/anomaly? result)
      result
      (->response config {:bank result}))))

(def ^:private command-handlers
  {"create-bank" create-bank
   "change-bank-tier" change-bank-tier
   "change-bank-status" change-bank-status})

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (error/reject :bank/unknown-command
                    (str "Unknown command: " command))
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (error/fail :bank/process-command
                      {:message "No schema found for command"
                       :command command})
          (let-nom> [data (avro/deserialize-same schema payload)]
            (handler config data)))))))

(defrecord BankProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
