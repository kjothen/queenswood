(ns com.repldriven.mono.accounts.commands
  (:refer-clojure :exclude [get load read update])
  (:require
    [com.repldriven.mono.accounts.domain :as domain]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn- payment-address-pb->avro
  "Flattens protojure oneof :identifier wrapper to flat
  Avro-compatible shape."
  [{:keys [scheme identifier]}]
  {:scheme scheme
   :scan (:scan identifier)
   :value (:value identifier)})

(defn- account-pb->avro
  "Converts protobuf Account to Avro-compatible map."
  [pb]
  (let [account (schema/pb->Account pb)]
    (clojure.core/update account
                         :payment-addresses
                         #(mapv payment-address-pb->avro %))))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "account")
                                (account-pb->avro result))})))

(defn- load
  "Loads a raw record by id from the store. Returns the
  protobuf record or a rejection anomaly if not found."
  [store account-id]
  (or (fdb/load-record store account-id)
      (error/reject :account/not-found "Account not found")))

(defn- load-customer-accounts
  "Returns the existing accounts for a customer."
  [store customer-id]
  (fdb/load-records store "Account" "customer_id" customer-id))

(defn- save
  "Saves account to store, writes changelog entry, returns
  protobuf record or anomaly."
  [store account]
  (error/let-nom> [_ (fdb/save-record store (schema/Account->java account))
                   _
                   (fdb/write-changelog store "accounts" (:account-id account))]
    (schema/Account->pb account)))

(defn- create
  "Loads customer accounts, applies f, saves. Returns
  protobuf record or anomaly."
  [config data f]
  (let [{:keys [record-db record-store]} config
        {:keys [customer-id]} data]
    (fdb/transact record-db
                  record-store
                  "accounts"
                  (fn [store]
                    (error/nom->> (load-customer-accounts store customer-id)
                                  (map schema/pb->Account)
                                  (f data)
                                  (save store))))))

(defn- read
  "Loads account by id. Returns protobuf record or anomaly."
  [config account-id]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  record-store
                  "accounts"
                  (fn [store] (load store account-id)))))

(defn- update
  "Loads account by id, applies f, saves back. Returns
  protobuf record or anomaly."
  [config account-id f]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  record-store
                  "accounts"
                  (fn [store]
                    (error/nom->> (load store account-id)
                                  schema/pb->Account
                                  f
                                  (save store))))))

(defn open
  "Opens a new account."
  [config data]
  (->response config (create config data domain/open-account)))

(defn get
  "Returns the current account or rejection anomaly."
  [config data]
  (let [{:keys [account-id]} data]
    (->response config (read config account-id))))

(defn close
  "Closes an account."
  [config data]
  (let [{:keys [account-id]} data]
    (->response config (update config account-id domain/close-account))))

