(ns com.repldriven.mono.fdb.store)

(defn open-store
  "Opens a record store by calling the store function (returned by
  the store or meta-store system component) with the given context
  and store name."
  [open-store-fn ctx store-name]
  (open-store-fn ctx store-name))
