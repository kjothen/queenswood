(ns com.repldriven.mono.accounts.commands.reporting-operations
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schema.interface :as schema]))

(defn- ->account-status
  [schemas status payload]
  {:status status
   :payload (avro/serialize (get schemas "account-status") payload)})

(defn- ->response
  "Converts protobuf bytes to an account-status response. Returns
  the result unchanged if it is an anomaly, or a rejection if nil."
  [result schemas rejection]
  (cond
   (error/anomaly? result)
   result

   (nil? result)
   {:status "REJECTED" :message rejection}

   :else
   (->> (schema/pb->Account result)
        (->account-status schemas "ACCEPTED"))))

(defn get-account-status
  "Returns the current status of an account."
  [config data]
  (let [{:keys [record-db record-store schemas]} config
        {:keys [account-id]} data]
    (-> (fdb/transact record-db
                      record-store
                      "accounts"
                      (fn [store] (fdb/load-record store account-id)))
        (->response schemas "Account not found"))))
