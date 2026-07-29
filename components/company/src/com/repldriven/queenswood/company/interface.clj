(ns com.repldriven.queenswood.company.interface
  "Company records cached from the registry of record, keyed by company
  number. Persistence only — fetching a profile is the registry
  adapter's job, and this brick never names one."
  (:require
    [com.repldriven.queenswood.company.store :as store]))

(defn save-company
  "Write `company` (kebab keys) to the `companies` FDB store, keyed by
  `company_number`. Returns the company, or a `:company/save` anomaly on
  write failure. `txn-or-config` matches `fdb/transact` semantics."
  [txn-or-config company]
  (store/save-company txn-or-config company))

(defn get-company
  "Read a previously-cached company record from FDB. Returns nil when the
  company has never been looked up, or a `:company/get` anomaly on read
  failure. `txn-or-config` matches `fdb/transact` semantics."
  [txn-or-config company-number]
  (store/get-company txn-or-config company-number))
