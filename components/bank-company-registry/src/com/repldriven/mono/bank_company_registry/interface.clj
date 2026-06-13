(ns com.repldriven.mono.bank-company-registry.interface
  "Registry-aware company lookup. Resolves a company number against a
  named company registry — currently only UK Companies House —
  fetching the profile from the registry of record and caching it in
  FDB. Validates the registry id and rejects unsupported ones. Knows
  nothing about HTTP requests: callers pass a plain config map."
  (:require
    [com.repldriven.mono.bank-company-registry.core :as core]))

(def default-registry
  "The registry id assumed when a caller does not name one."
  core/default-registry)

(def available-registries
  "The supported company registries, each a descriptor map with
  `:registry-id` and `:name`."
  core/available-registries)

(defn lookup-company
  "Validate `registry-id` and resolve `company-number` against it.
  Fetches the company profile from the registry of record and caches
  it in the `companies` FDB store keyed by `company_number`. Returns
  the stored record map (kebab keys), or an anomaly:
  `:company-registry/registry-not-found` if the registry id is not
  supported, `:company-registry/company-not-found` if the registry has
  no such company, `:company-registry/http` for any other upstream
  non-2xx, `:company-registry/parse` if the body is not valid JSON,
  `:company-registry/save` on FDB write failure.

  `config` carries the registry connection details plus FDB access:
  `:companies-house-url`, `:record-db`, and `:record-store`."
  [config registry-id company-number]
  (core/lookup-company config registry-id company-number))

(defn get-company
  "Read a previously-cached company record from FDB. Returns nil when
  the company has never been looked up, or
  `:company-registry/registry-not-found` if the registry id is not
  supported. `txn-or-config` matches `fdb/transact` semantics."
  [txn-or-config registry-id company-number]
  (core/get-company txn-or-config registry-id company-number))
