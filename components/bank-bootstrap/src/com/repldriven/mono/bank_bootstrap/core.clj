(ns com.repldriven.mono.bank-bootstrap.core
  (:require
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface
     :as organizations]
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
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

(def ^:private tier-type->keyword
  {"system" :tier-type-system
   "micro" :tier-type-micro
   "standard" :tier-type-standard})

(defn- find-tier
  "Returns the Tier for the given tier-type keyword,
  or nil."
  [{:keys [record-db record-store]} tier-type]
  (error/try-nom
   :tier/find
   "Failed to find tier"
   (fdb/transact
    record-db
    record-store
    "tiers"
    (fn [store]
      (first (mapv schema/pb->Tier
                   (fdb/query-records store
                                      "Tier"
                                      "tier_type"
                                      tier-type)))))))

(defn- create-tier
  "Creates a Tier record in FDB."
  [{:keys [record-db record-store]} tier-type
   {:keys [policies limits]}]
  (let [now (System/currentTimeMillis)
        record {:tier-id (encryption/generate-id "tier")
                :tier-type tier-type
                :policies (vec (or policies []))
                :limits (vec (or limits []))
                :created-at now
                :updated-at now}]
    (error/try-nom
     :tier/create
     "Failed to create tier"
     (fdb/transact
      record-db
      record-store
      "tiers"
      (fn [store]
        (fdb/save-record store
                         (schema/Tier->java record))))
     record)))

(defn- ensure-tiers
  "Idempotent: creates tiers from the seed config."
  [config tiers-config]
  (doseq [[tier-name tier-data] tiers-config]
    (let [tier-type (or (tier-type->keyword (name tier-name))
                        :tier-type-unknown)
          existing (find-tier config tier-type)]
      (if (and existing (not (error/anomaly? existing)))
        (log/info "Tier exists:" (name tier-name))
        (let [result (create-tier config tier-type tier-data)]
          (if (error/anomaly? result)
            (log/error "Failed to create tier:"
                       (name tier-name)
                       result)
            (log/info "Created tier:" (name tier-name))))))))

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
  "Idempotent bootstrap: ensures tiers, internal
  organization, account, and balances exist. Returns
  map of IDs or anomaly."
  [config seed]
  (log/info "Bootstrap starting")
  (let [{:keys [currencies balances tiers]} seed
        organization-name (get-in seed [:organization :name])
        tier-type (get-in seed
                          [:organization :tier]
                          :tier-type-system)]
    (ensure-tiers config tiers)
    (let-nom>
      [existing (find-organization config organization-name)
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
                     tier-type
                     currencies)
            result (org->result (:organization created))
            _ (update-balances config
                               (:account-id result)
                               balances)]
           (log/info "Created bootstrap organization:"
                     (:organization-id result))
           result))]
      (log/info "Bootstrap complete")
      (select-keys result result-keys))))
