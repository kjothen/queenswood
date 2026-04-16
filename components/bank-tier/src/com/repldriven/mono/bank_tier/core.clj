(ns com.repldriven.mono.bank-tier.core
  (:require
    [com.repldriven.mono.bank-tier.domain :as domain]
    [com.repldriven.mono.bank-tier.store :as store]

    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]))

(defn get-tiers
  "Lists all tiers. Returns a sequence of tier maps or
  anomaly."
  [txn]
  (store/get-tiers txn))

(defn get-tier
  "Finds a Tier by tier-type keyword. Returns the Tier
  map or anomaly."
  [txn tier-type]
  (store/get-tier txn tier-type))

(defn new-tier
  "Creates a new Tier with the given type, policies, and
  limits. Returns the Tier map or anomaly."
  [txn tier-type policies limits]
  (store/transact txn
                  (fn [txn]
                    (let [tier (domain/new-tier tier-type policies limits)]
                      (let-nom> [_ (store/create txn tier)
                                 result (get-tier txn tier-type)]
                        result)))))

(defn get-org-tier
  "Loads the tier for the given organization. Returns the
  Tier map or rejection anomaly."
  [txn org-id]
  (store/get-org-tier txn org-id))

(defn update-tier
  "Updates a tier's policies and limits. Returns the
  updated tier map or anomaly."
  [txn tier-type policies limits]
  (store/transact txn
                  (fn [txn]
                    (let-nom>
                      [existing (get-tier txn tier-type)
                       updated (assoc existing
                                      :policies (vec policies)
                                      :limits (vec limits)
                                      :updated-at (System/currentTimeMillis))
                       _ (store/save txn updated)]
                      updated))))
