(ns com.repldriven.mono.bank-api.tier.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer
     [examples-registry]]))

(def registry (examples-registry []))

(def Policy {:capability :account-opening :effect :allow})

(def Limit
  {:type :max-accounts
   :kind {:account-type :current}
   :value 1000
   :reason "Maximum 1000 current accounts"})

(def Tier
  {:tier-type :micro
   :policies [Policy]
   :limits [Limit]
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def TierList {:tiers [Tier]})

(def ReplaceTierRequest {:policies [Policy] :limits [Limit]})
