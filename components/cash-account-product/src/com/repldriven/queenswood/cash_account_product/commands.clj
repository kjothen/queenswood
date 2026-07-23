(ns com.repldriven.queenswood.cash-account-product.commands
  (:require
    [com.repldriven.queenswood.cash-account-product.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "cash-account-product") result)})))

(def ^:private command-handlers
  {"create-cash-account-product"
   (fn [config data]
     (let [{:keys [bank-id]} data]
       (->response config (core/new-product config bank-id data))))
   "open-cash-account-product-draft"
   (fn [config data]
     (let [{:keys [bank-id product-id]} data]
       (->response config (core/open-draft config bank-id product-id data))))
   "update-cash-account-product-draft"
   (fn [config data]
     (let [{:keys [bank-id product-id version-id]} data]
       (->response
        config
        (core/update-draft config bank-id product-id version-id data))))
   "discard-cash-account-product-draft"
   (fn [config data]
     (let [{:keys [bank-id product-id version-id]} data]
       (->response config
                   (core/discard-draft config bank-id product-id version-id))))
   "publish-cash-account-product"
   (fn [config data]
     (let [{:keys [bank-id product-id version-id]} data]
       (->response config
                   (core/publish config bank-id product-id version-id))))})

(defn- dispatch
  [config message]
  (let [{:keys [command id payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (error/reject :cash-account-product/unknown-command
                    (str "Unknown command: " command))
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (error/fail :cash-account-product/process-command
                      {:message "No schema found for command"
                       :command command})
          (let-nom> [raw (avro/deserialize-same schema payload)
                     data (assoc raw :idempotency-key id)]
            (handler config data)))))))

(defrecord CashAccountProductProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
