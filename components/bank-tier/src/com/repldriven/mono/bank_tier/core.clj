(ns com.repldriven.mono.bank-tier.core
  (:require
    [com.repldriven.mono.bank-tier.domain :as domain]
    [com.repldriven.mono.bank-tier.store :as store]

    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]))

(defn get-tiers
  "Lists all tiers. Returns a sequence of tier maps or
  anomaly."
  [config]
  (store/get-tiers config))

(defn get-tier
  "Finds a Tier by tier-type keyword. Returns the Tier
  map, nil if not found, or anomaly."
  [config tier-type]
  (store/get-tier config tier-type))

(defn new-tier
  "Creates a new Tier with the given type, policies, and
  limits. Returns the Tier map or anomaly."
  [config tier-type policies limits]
  (let [tier (domain/new-tier tier-type policies limits)]
    (let-nom> [_ (store/create config tier)
               result (get-tier config tier-type)]
      result)))

(defn update-tier
  "Updates a tier's policies and limits. Returns the
  updated tier map or anomaly."
  [config tier-type policies limits]
  (let-nom>
    [existing (or (get-tier config tier-type)
                  (error/fail :tier/not-found
                              {:message "Tier not found"
                               :tier-type tier-type}))
     updated (assoc existing
                    :policies (vec policies)
                    :limits (vec limits)
                    :updated-at (System/currentTimeMillis))
     _ (store/save config updated)]
    updated))
