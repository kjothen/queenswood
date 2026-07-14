(ns com.repldriven.mono.bank-party.watcher
  (:require
    [com.repldriven.mono.bank-party.store :as store]))

(defn on-idv-check-changed
  "Handler for bank-idv's changelog cursor. Receives the changed
  check record."
  [txn record-db check]
  ;; TODO: activate the party once its IDV check completes
  nil)
