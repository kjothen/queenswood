(ns com.repldriven.mono.bank-cash-account.restriction
  "Tier-derived restrictions on cash-account lifecycle — policy
  gates (capability: allow/deny) and quantitative limits. Returns
  nil when the caller is unrestricted, rejection anomaly otherwise,
  so callers can chain through `let-nom>`."
  (:require
    [com.repldriven.mono.bank-tier.interface :as tiers]

    [com.repldriven.mono.error.interface :as error]))

(defn policy-account-opening
  "Checks the account-opening policy. Returns nil if allowed,
  anomaly if denied or not found."
  [tier]
  (let [policy (tiers/policy tier :policy-capability-account-opening)]
    (if (error/anomaly? policy)
      policy
      (when (= :policy-effect-deny (:effect policy))
        (error/reject
         :cash-account/policy-denied
         {:message (if (seq (:reason policy))
                     (:reason policy)
                     "Account opening denied by policy")
          :capability
          :policy-capability-account-opening})))))

(defn policy-account-closing
  "Checks the account-closing policy. Returns nil if allowed,
  anomaly if denied or not found."
  [tier]
  (let [policy (tiers/policy tier
                             :policy-capability-account-closure)]
    (if (error/anomaly? policy)
      policy
      (when (= :policy-effect-deny (:effect policy))
        (error/reject
         :cash-account/policy-denied
         {:message (if (seq (:reason policy))
                     (:reason policy)
                     "Account closing denied by policy")
          :capability
          :policy-capability-account-closure})))))

(defn limit-max-accounts
  "Checks the max-accounts limit for the given product-type against
  the current count. Returns nil if under limit or no limit
  defined, anomaly if exceeded."
  [tier product-type account-count]
  (when-let [{:keys [value reason]}
             (tiers/limit tier
                          :limit-type-max-accounts
                          {:product-type product-type})]
    (when (>= account-count value)
      (error/reject :cash-account/limit-max-accounts
                    {:message reason
                     :kind product-type
                     :limit value}))))
