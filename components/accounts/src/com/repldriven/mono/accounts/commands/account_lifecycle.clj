(ns com.repldriven.mono.accounts.commands.account-lifecycle
  (:refer-clojure :exclude [load update])
  (:require
    [com.repldriven.mono.accounts.commands.response :refer [->account]]
    [com.repldriven.mono.accounts.domain :as domain]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schema.interface :as schema]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- ->data
  "Converts a protojure account map to a keyword-keyed wire map."
  [account]
  {:account-id (:account-id account)
   :customer-id (:customer-id account)
   :name (:name account)
   :currency (:currency account)})

(defn- ->response
  "Converts protobuf bytes to an account response. Returns the
  result unchanged if it is an anomaly, or a rejection if nil."
  [result schemas rejection]
  (cond
   (error/anomaly? result)
   result

   (nil? result)
   {:status "REJECTED" :message rejection}

   :else
   (->> (schema/pb->Account result)
        ->data
        (->account schemas "ACCEPTED"))))

(defn- load
  "Loads an account by id from the store, returning a Clojure
  map or nil if not found."
  [store account-id]
  (some-> (fdb/load-record store account-id)
          schema/pb->Account))

(defn- save
  "Saves account to store, writes changelog entry, returns protobuf bytes."
  [store ctx account]
  (fdb/save-record store (schema/Account->java account))
  (fdb/write-changelog ctx "accounts" (:account-id account))
  (schema/Account->pb account))

(defn- update
  "Loads account by id, applies f, saves back. Returns protobuf
  bytes, nil if not found, or anomaly on failure."
  [config account-id f]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  (fn [ctx]
                    (let [store (record-store ctx "accounts")]
                      (some->> (load store account-id)
                               f
                               (save store ctx)))))))

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
        {:keys [customer-id name currency]} data
        account-id (str (utility/uuidv7))]
    (fdb/transact
     record-db
     (fn [ctx]
       (let [store (record-store ctx "accounts")]
         (when-not (customer-exists? store customer-id)
           (->> (domain/new-account account-id customer-id name currency)
                (save store ctx))))))))

(defn open
  "Inserts a new account record with status open."
  [config data]
  (let [{:keys [schemas]} config]
    (-> (create config data)
        (->response schemas "Account already exists for customer"))))

(defn- update-status
  "Updates account status and returns an account response."
  [config data status]
  (let [{:keys [account-id]} data
        {:keys [schemas]} config]
    (-> (update config
                account-id
                (fn [account] (domain/set-status account status)))
        (->response schemas "Account not found"))))

(defn close
  "Sets account status to closed."
  [config data]
  (update-status config data "closed"))

(defn reopen
  "Sets account status back to open."
  [config data]
  (update-status config data "open"))

(defn suspend
  "Sets account status to suspended."
  [config data]
  (update-status config data "suspended"))

(defn unsuspend
  "Sets account status back to open from suspended."
  [config data]
  (update-status config data "open"))

(defn archive
  "Sets account status to archived."
  [config data]
  (update-status config data "archived"))
