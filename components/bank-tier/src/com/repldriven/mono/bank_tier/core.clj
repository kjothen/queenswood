(ns com.repldriven.mono.bank-tier.core
  (:require
    [com.repldriven.mono.bank-tier.domain :as domain]
    [com.repldriven.mono.bank-tier.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn get-tiers
  "Lists all tiers. Returns a sequence of tier maps or
  anomaly."
  [txn]
  (store/get-tiers txn))

(defn get-tier
  "Finds a Tier by tier-id. Returns the Tier map or
  anomaly."
  [txn tier-id]
  (store/get-tier txn tier-id))

(defn new-tier
  "Creates a new Tier with the given name, policies, and
  limits. Returns the persisted tier or anomaly."
  [txn name policies limits]
  (store/transact txn
                  (fn [txn]
                    (let [tier (domain/new-tier name policies limits)]
                      (let-nom> [_ (store/create txn tier)
                                 result (get-tier txn (:tier-id tier))]
                        result)))))

(defn get-org-tier
  "Loads the tier for the given organization. Returns the
  Tier map or rejection anomaly."
  [txn org-id]
  (store/get-org-tier txn org-id))

(defn update-tier
  "Updates a tier's policies and limits. Returns the
  updated tier map or anomaly."
  [txn tier-id policies limits]
  (store/transact txn
                  (fn [txn]
                    (let-nom>
                      [existing (get-tier txn tier-id)
                       updated (assoc existing
                                      :policies (vec policies)
                                      :limits (vec limits)
                                      :updated-at (System/currentTimeMillis))
                       _ (store/save txn updated)]
                      updated))))
