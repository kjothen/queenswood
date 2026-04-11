(ns com.repldriven.mono.bank-organization.domain
  (:require
    [com.repldriven.mono.bank-tier.interface :as tiers]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- limit-max-organizations
  "Checks the max-organizations limit for the given
  org-type against the current count. Returns nil if
  under limit or no limit defined, anomaly if exceeded."
  [tier org-type org-count]
  (when-let [{:keys [value reason]}
             (tiers/limit tier
                          :limit-type-max-organizations
                          {:organization-type org-type})]
    (when (>= org-count value)
      (error/fail :organization/limit-max-organizations
                  {:message reason
                   :kind org-type
                   :limit value}))))

(defn new-organization
  "Creates a new Organization record map."
  [org-name org-type tier org-count]
  (let-nom>
    [_ (limit-max-organizations tier org-type org-count)]
    (let [now (System/currentTimeMillis)]
      {:organization-id (encryption/generate-id "org")
       :name org-name
       :type org-type
       :tier-type (:tier-type tier)
       :status "active"
       :created-at now
       :updated-at now})))
