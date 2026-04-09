(ns com.repldriven.mono.bank-organization.interface
  (:require
    [com.repldriven.mono.bank-organization.core :as core]))

(defn new-organization
  "Creates an organization with API key, internal party,
  product, and one cash account per currency. Returns map
  or anomaly.

  opts may include :policies and :limits to seed
  organization-level restrictions."
  ([config org-name org-type currencies]
   (core/new-organization config
                          org-name
                          org-type
                          currencies
                          {}))
  ([config org-name org-type currencies opts]
   (core/new-organization config
                          org-name
                          org-type
                          currencies
                          opts)))

(defn get-organizations
  "Lists organizations enriched with party, accounts, and
  api-key. Returns a sequence of rich organization maps or
  anomaly."
  [config]
  (core/get-organizations config))
