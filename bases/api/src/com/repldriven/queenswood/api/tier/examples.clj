(ns com.repldriven.queenswood.api.tier.examples
  (:require
    [com.repldriven.queenswood.api.schema :refer [examples-registry]]))

(def registry (examples-registry []))

(def Tier
  {:tier "micro" :description "Micro tier policy - capabilities and limits"})

(def TierList {:tiers [Tier]})
