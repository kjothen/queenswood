(ns com.repldriven.mono.fdb.interface
  "FoundationDB Record Layer wrapper. Exposes record-store
  open/save/load/scan plus a `transact` macro that runs a body
  inside a single FDB transaction.")

(defmacro transact
  "Runs body inside a single FDB transaction bound to txn-sym.
  record-db is the store's opened record database.

  (transact [txn record-db]
    (store/save-account txn record-db account)
    (store/save-ledger-entry txn record-db entry))"
  [[txn-sym record-db] & body]
  `(let [~txn-sym (open-txn ~record-db)]
     ~@body))
