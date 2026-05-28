(ns com.repldriven.mono.bank-company-check.interface
  "Look up a UK Companies House company profile and cache it in
  FDB. The component does not know whether `:api-url` points at the
  real Companies House API or a local simulator — both are
  interchangeable."
  (:require
    [com.repldriven.mono.bank-company-check.core :as core]
    [com.repldriven.mono.bank-company-check.store :as store]))

(defn check-company
  "Fetch the Companies House profile for `company-number` from
  `(:api-url config)` and persist it in the `companies` FDB store
  keyed by `company_number`. Returns the stored record map (kebab
  keys), or an anomaly: `:company-check/not-found` if the upstream
  returns 404, `:company-check/http` if the upstream returns any
  other non-2xx, `:company-check/parse` if the response body is not
  valid JSON, `:company-check/save` on FDB write failure.

  `config` carries `:api-url` plus the `:record-db` and
  `:record-store` keys that `fdb/transact` needs to open a
  transaction (see `com.repldriven.mono.fdb.interface/transact`)."
  [config company-number]
  (core/check-company config company-number))

(defn get-company
  "Read a previously-stored company record from FDB. Returns nil
  when the company has never been checked. `txn-or-config` matches
  `fdb/transact` semantics."
  [txn-or-config company-number]
  (store/get-company txn-or-config company-number))
