(ns com.repldriven.mono.bank-bank.system
  (:require
    [com.repldriven.mono.bank-bank.core :as core]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private bank
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [record-db record-store name type status
                                     tier policy currencies]}
                             config
                             txn {:record-db record-db
                                  :record-store record-store}]
                         (let-nom> [existing (core/get-banks-by-type txn type)]
                           (if-let [b (first existing)]
                             (core/get-bank txn b)
                             (core/new-bank txn
                                            name
                                            type
                                            (or status :bank-status-test)
                                            tier
                                            currencies
                                            {:policies [policy]}))))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :policy system/required-component}
   :system/instance-schema map?})

(def ^:private bank-from-fdb
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [record-db record-store type]} config
               txn {:record-db record-db :record-store record-store}]
           (let-nom> [banks (core/get-banks-by-type txn type)]
             (if-let [b (first banks)]
               (core/get-bank txn b)
               (error/fail :banks/bank-not-found
                           {:message "Bank of given type not found in FDB"
                            :type type}))))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(def ^:private internal-account-id
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (get-in (:bank config) [:bank :accounts 0 :account-id])))
   :system/config {:bank system/required-component}
   :system/instance-schema string?})

(system/defcomponents :banks
                      {:bank bank
                       :bank-from-fdb bank-from-fdb
                       :internal-account-id internal-account-id})
