(ns com.repldriven.mono.bank-api.tier.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer
     [examples-registry]]))

(def TierNotFound
  {:value {:title "REJECTED"
           :type "tier/not-found"
           :status 404
           :detail "Tier not found"}})

(def registry (examples-registry [#'TierNotFound]))

(def TierId "tie.01kprbmgcj35ptc8npmybhh4t0")

(def Policy {:capability :account-opening :effect :allow})

(def Limit
  {:type :max-accounts
   :kind {:product-type :current}
   :value 1000
   :reason "Maximum 1000 current accounts"})

(def Tier
  {:tier-id TierId
   :name "Starter"
   :policies [Policy]
   :limits [Limit]
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def TierList {:tiers [Tier]})

(def CreateTierRequest {:name "Starter" :policies [Policy] :limits [Limit]})

(def ReplaceTierRequest {:policies [Policy] :limits [Limit]})
