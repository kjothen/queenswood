(ns com.repldriven.mono.accounts.commands.account-lifecycle
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.accounts.domain :as domain]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schema.interface :as schema]))

(defn- ->response
  "Converts protobuf bytes to an account response. Returns
  the result unchanged if it is an anomaly (FAILED), or a
  rejection if nil."
  [result schemas rejection]
  (cond
   (error/anomaly? result)
   result

   (nil? result)
   {:status "REJECTED" :message rejection}

   :else
   {:status "ACCEPTED"
    :payload (avro/serialize (get schemas "account")
                             (schema/pb->Account result))}))

(defn- load
  "Loads an account by id from the store, returning a
  Clojure map or nil if not found."
  [store account-id]
  (some-> (fdb/load-record store account-id)
          schema/pb->Account))

(defn- save
  "Saves account to store, writes changelog entry, returns
  protobuf bytes."
  [store account]
  (fdb/save-record store (schema/Account->java account))
  (fdb/write-changelog store "accounts" (:account-id account))
  (schema/Account->pb account))

(defn- update-status
  "Loads account by id, sets status, saves back. Returns
  protobuf bytes, nil if not found, or anomaly on failure."
  [config account-id status]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  record-store
                  "accounts"
                  (fn [store]
                    (some->> (load store account-id)
                             (#(domain/set-status % status))
                             (save store))))))

(defn- customer-exists?
  "Returns truthy if an account with the given customer-id
  already exists in the store."
  [store customer-id]
  (seq (fdb/query-records store "Account" "customer_id" customer-id)))

(defn- create
  "Creates account if customer-id is unique. Returns protobuf
  bytes or nil if customer already exists."
  [config data]
  (let [{:keys [record-db record-store]} config
        {:keys [customer-id]} data]
    (fdb/transact
     record-db
     record-store
     "accounts"
     (fn [store]
       (some->> (domain/new-account (customer-exists? store customer-id) data)
                (save store))))))

(defn open
  "Inserts a new account record with status opening."
  [config data]
  (let [{:keys [schemas]} config]
    (-> (create config data)
        (->response schemas "Account already exists for customer"))))

(defn close
  "Sets account status to closing."
  [config data]
  (let [{:keys [account-id]} data
        {:keys [schemas]} config]
    (-> (update-status config account-id "closing")
        (->response schemas "Account not found"))))
