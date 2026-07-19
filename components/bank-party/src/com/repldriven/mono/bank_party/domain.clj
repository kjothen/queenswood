(ns com.repldriven.mono.bank-party.domain
  (:refer-clojure :exclude [type])
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-party
  [data]
  (let [{:keys [bank-id type display-name]} data
        now (System/currentTimeMillis)
        status (if (= :party-type-person type)
                 :party-status-pending
                 :party-status-active)]
    {:bank-id bank-id
     :party-id (utility/generate-id "pty")
     :type type
     :display-name display-name
     :status status
     :created-at now
     :updated-at now}))

(defn activate-party
  [party]
  (assoc party
         :status :party-status-active
         :updated-at (System/currentTimeMillis)))

(defn reject-party
  [party]
  (assoc party
         :status :party-status-rejected
         :updated-at (System/currentTimeMillis)))

(defn new-party-national-identifier
  [national-identifier bank-id party-id]
  (let [{:keys [type value issuing-country]} national-identifier]
    {:bank-id bank-id
     :party-id party-id
     :type type
     :value value
     :issuing-country issuing-country
     :created-at (System/currentTimeMillis)}))

(defn- check-capability
  [action policies]
  (policy/check-capability policies :party {:action action}))

(defn merge-party
  "Merge `merged-away` into `survivor`: a tombstone-plus-pointer, not a
  rewrite. The survivor must be active and the merged-away party must
  already be suspended (an operator quiesces a record before merging
  it away) — both source-state guards run before the capability check,
  per the lifecycle-transitions convention. `has-open-accounts?` is
  the merged-away party's non-closed cash-account check, resolved by
  the caller via `bank-cash-account-query/find-accounts-by-party`.

  IDV/KYC and other party-linked records are untouched — they keep
  referencing the original party-id; `merged-into-party-id` is the
  durable audit link a reader follows to the survivor."
  [survivor merged-away has-open-accounts? policies]
  (let-nom>
    [_ (when (= (:party-id survivor) (:party-id merged-away))
         (error/reject :party/merge-into-self
                       {:message "Cannot merge a party into itself"
                        :party-id (:party-id merged-away)}))
     _ (when-not (= :party-status-suspended (:status merged-away))
         (error/reject :party/invalid-status
                       {:message "Party is not in a mergeable state"
                        :party-id (:party-id merged-away)
                        :status (:status merged-away)
                        :allowed #{:party-status-suspended}}))
     _ (when-not (= :party-status-active (:status survivor))
         (error/reject :party/invalid-status
                       {:message "Survivor party is not active"
                        :party-id (:party-id survivor)
                        :status (:status survivor)
                        :allowed #{:party-status-active}}))
     _ (check-capability :party-action-merge policies)
     _ (when has-open-accounts?
         (error/reject :party/open-accounts
                       {:message "Party has open cash accounts"
                        :party-id (:party-id merged-away)}))]
    (assoc merged-away
           :status :party-status-merged
           :merged-into-party-id (:party-id survivor)
           :updated-at (utility/now))))

