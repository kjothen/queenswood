(ns com.repldriven.mono.accounts.commands.reporting-operations
  (:require
    [com.repldriven.mono.accounts.commands.response
     :refer [->account-status]]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schema.interface :as schema]))

(defn- ->data
  "Converts a protojure account map to a string-keyed wire map."
  [account]
  {"account_id" (:account-id account) "account_status" (:status account)})

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
        ->data
        (->account-status schemas "ACCEPTED"))))

(defn get-account-status
  "Returns the current status of an account."
  [config data]
  (let [{:keys [record-db record-store schemas]} config
        {:strs [account_id]} data]
    (-> (fdb/load-record record-db record-store "accounts" account_id)
        (->response schemas "Account not found"))))
