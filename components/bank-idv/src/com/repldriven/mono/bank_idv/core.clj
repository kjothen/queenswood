(ns com.repldriven.mono.bank-idv.core
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn initiate
  "Initiates a new IDV. Returns the IDV map or anomaly."
  [txn data]
  (let-nom> [idv (domain/new-idv data)]
    (store/save-idv txn
                    idv
                    {:verification-id (:verification-id idv)
                     :status-after (:status idv)})))

(defn get
  "Returns the current IDV or rejection anomaly."
  [txn data]
  (let [{:keys [organization-id verification-id]} data]
    (store/get-idv txn organization-id verification-id)))
