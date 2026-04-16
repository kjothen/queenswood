(ns com.repldriven.mono.bank-organization.restriction
  (:require
    [com.repldriven.mono.bank-tier.interface :as tiers]

    [com.repldriven.mono.error.interface :as error]))

(defn limit-max-organizations
  "Checks the max-organizations limit for the given
  org-type against the current count. Returns nil if
  under limit or no limit defined, anomaly if exceeded."
  [tier org-type org-count]
  (when-let [{:keys [value reason]}
             (tiers/limit tier
                          :limit-type-max-organizations
                          {:organization-type org-type})]
    (when (>= org-count value)
      (error/reject :organization/limit-max-organizations
                    {:message reason
                     :kind org-type
                     :limit value}))))
