(ns com.repldriven.mono.bank-cash-account.restriction
  (:require
    [com.repldriven.mono.bank-tier.interface :as tiers]

    [com.repldriven.mono.error.interface :as error]))

(defn valid-currency?
  "Returns true if currency is allowed by product,
  rejection anomaly otherwise."
  [currency product]
  (let [allowed (:allowed-currencies product)]
    (if (and (seq allowed)
             (not (some #{currency} allowed)))
      (error/reject :cash-account/invalid-currency
                    "Currency not allowed for this product")
      true)))

(defn- enum-suffix
  "Strips a keyword prefix from a keyword name.
  (enum-suffix :party-status-active :party-status)
  => \"active\""
  [kw prefix]
  (subs (name kw) (inc (count (name prefix)))))

(defn valid-party?
  "Returns true if party is active, rejection anomaly
  otherwise."
  [party]
  (let [status (:status party)]
    (if (not= :party-status-active status)
      (let [s (enum-suffix status :party-status)]
        (error/reject :cash-account/party-status
                      (str "Party is " s)))
      true)))

(defn policy-account-opening
  "Checks the account-opening policy. Returns nil if
  allowed, anomaly if denied or not found."
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
  "Checks the account-closing policy. Returns nil if
  allowed, anomaly if denied or not found."
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
  "Checks the max-accounts limit for the given
  account-type against the current count. Returns nil
  if under limit or no limit defined, anomaly if
  exceeded."
  [tier account-type account-count]
  (when-let [{:keys [value reason]}
             (tiers/limit tier
                          :limit-type-max-accounts
                          {:account-type account-type})]
    (when (>= account-count value)
      (error/reject :cash-account/limit-max-accounts
                    {:message reason
                     :kind account-type
                     :limit value}))))
