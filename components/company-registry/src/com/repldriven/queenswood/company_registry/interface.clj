(ns com.repldriven.queenswood.company-registry.interface
  "Company lookup against the registry of record. Fetches a company
  profile by number and caches it in FDB. Knows nothing about HTTP
  requests: callers pass a plain config map."
  (:require
    [com.repldriven.queenswood.company-registry.core :as core]))

(defn lookup-company
  "Resolve `company-number` against the registry of record. Fetches the
  company profile and caches it in the `companies` FDB store keyed by
  `company_number`. Returns the stored record map (kebab keys), or an
  anomaly: `:company-registry/company-not-found` if the registry has no
  such company, `:company-registry/http` for any other upstream non-2xx,
  `:company-registry/parse` if the body is not valid JSON,
  `:company-registry/save` on FDB write failure.

  `config` carries the registry connection details plus FDB access:
  `:companies-house-url`, `:record-db`, and `:record-store`."
  [config company-number]
  (core/lookup-company config company-number))

(defn get-company
  "Read a previously-cached company record from FDB. Returns nil when
  the company has never been looked up. `txn-or-config` matches
  `fdb/transact` semantics."
  [txn-or-config company-number]
  (core/get-company txn-or-config company-number))
