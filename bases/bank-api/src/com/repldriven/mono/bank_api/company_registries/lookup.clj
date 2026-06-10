(ns com.repldriven.mono.bank-api.company-registries.lookup
  "Shared company-registry lookup used by the lookup endpoint and by
  onboarding (which re-fetches to snapshot the bound entity)."
  (:require
    [com.repldriven.mono.bank-company-check.interface :as company-check]
    [com.repldriven.mono.error.interface :as error]))

(def supported-registry "uk-companies-house")

(defn- ch-config
  [{:keys [companies-house-url record-db record-store]}]
  {:api-url companies-house-url
   :record-db record-db
   :record-store record-store})

(defn find-company
  "Validate `registry-id` and resolve `company-number` against it.
  Returns the company profile map (kebab keys) or an anomaly:
  `:company-registry/not-found` for an unsupported registry, else
  whatever `company-check/check-company` returns
  (`:company-check/not-found` on a 404, upstream errors otherwise)."
  [request registry-id company-number]
  (if (not= supported-registry registry-id)
    (error/reject :company-registry/not-found
                  {:message "Company registry not supported"
                   :registry registry-id})
    (company-check/check-company (ch-config request) company-number)))
