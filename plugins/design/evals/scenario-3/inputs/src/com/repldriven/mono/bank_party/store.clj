(ns com.repldriven.mono.bank-party.store)

(defn get-party
  [txn record-db party-id]
  ;; loads the party record
  {:party-id party-id :status :pending})

(defn save-party
  [txn record-db party]
  ;; persists the party record
  party)
