(ns com.repldriven.mono.bank-ledger.store)

(defn save-account
  "Persists a debited account record. Takes the live txn, the
  record-db, and the updated account map."
  [txn record-db account]
  ;; writes to the `accounts` record store
  account)

(defn save-ledger-entry
  "Persists a new ledger entry. Takes the live txn, the record-db,
  and the entry map."
  [txn record-db entry]
  ;; writes to the `ledger` record store
  entry)
