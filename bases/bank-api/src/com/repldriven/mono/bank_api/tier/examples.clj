(ns com.repldriven.mono.bank-api.tier.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def registry (examples-registry []))

(def Tier
  {:tier "micro" :description "Micro tier policy - capabilities and limits"})

(def TierList {:tiers [Tier]})
