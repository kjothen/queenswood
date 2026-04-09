(ns com.repldriven.mono.bank-bootstrap.core
  (:require
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface
     :as organizations]
    [com.repldriven.mono.bank-restriction.interface
     :as restriction]

    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]))

(defn- find-organization
  "Returns the first organization matching name, or nil."
  [config name]
  (let-nom> [orgs (organizations/get-organizations config)]
    (->> orgs
         (filter #(= name (:name %)))
         first)))

(defn- update-balance
  "Updates a balance to match the seed definition.
  Rejects if the balance does not exist."
  [config account-id balance]
  (let [{:keys [balance-type balance-status currency]}
        balance]
    (let-nom>
      [_ (balances/update-balance
          config
          (assoc balance :account-id account-id))]
      (log/info "Updated bootstrap balance:"
                balance-type
                currency
                balance-status))))

(defn- update-balances
  "Updates balances that already exist. Returns nil or
  the first anomaly."
  [config account-id balances]
  (reduce (fn [_ balance]
            (let [result (update-balance config
                                         account-id
                                         balance)]
              (if (error/anomaly? result)
                (reduced result)
                result)))
          nil
          balances))

(defn- ensure-restrictions
  "Idempotent: creates restrictions for owner-id if none
  exist."
  [config owner-id organization-id restrictions]
  (when (seq restrictions)
    (let [existing (restriction/get-restrictions
                    config
                    owner-id)]
      (if existing
        (do (log/info "Restrictions exist for" owner-id)
            existing)
        (let [result (restriction/new-restrictions
                      config
                      owner-id
                      (assoc restrictions
                             :organization-id
                             organization-id))]
          (log/info "Created restrictions for" owner-id)
          result)))))

(def ^:private result-keys
  [:organization-id
   :party-id
   :product-id
   :version-id
   :account-id])

(defn- org->result
  "Extracts bootstrap result keys from a rich
  organization map."
  [org]
  (let [account (first (:accounts org))]
    {:organization-id (:organization-id org)
     :party-id (get-in org [:party :party-id])
     :product-id (:product-id account)
     :version-id (:version-id account)
     :account-id (:account-id account)}))

(defn bootstrap
  "Idempotent bootstrap: ensures internal organization,
  account, balances, and restrictions exist. Returns map
  of IDs or anomaly."
  [config seed]
  (log/info "Bootstrap starting")
  (let [{:keys [currencies balances]} seed
        organization-name (get-in seed [:organization :name])
        sys-restrictions (get-in seed [:system :restrictions])
        org-restrictions (get-in seed
                                 [:organization :restrictions])]
    (let-nom>
      [_ (ensure-restrictions config
                              "system"
                              "system"
                              sys-restrictions)
       existing (find-organization config organization-name)
       result
       (if existing
         (do (log/info "Bootstrap organization exists:"
                       (:organization-id existing))
             (org->result existing))
         (let-nom>
           [created (organizations/new-organization
                     config
                     organization-name
                     :organization-type-internal
                     currencies)
            result (org->result (:organization created))
            _ (update-balances config
                               (:account-id result)
                               balances)]
           (log/info "Created bootstrap organization:"
                     (:organization-id result))
           result))
       _ (ensure-restrictions config
                              (:organization-id result)
                              (:organization-id result)
                              org-restrictions)]
      (log/info "Bootstrap complete")
      (select-keys result result-keys))))
