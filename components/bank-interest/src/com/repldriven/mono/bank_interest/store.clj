(ns com.repldriven.mono.bank-interest.store
  (:require
    [com.repldriven.mono.fdb.interface :as fdb]))

(def transact fdb/transact)
