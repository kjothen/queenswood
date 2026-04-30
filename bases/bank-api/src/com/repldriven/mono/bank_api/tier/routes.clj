(ns com.repldriven.mono.bank-api.tier.routes
  (:require
    [com.repldriven.mono.bank-api.tier.queries :as queries]))

(def routes
  [["/tiers"
    {:openapi {:tags ["Tiers"] :security [{"adminAuth" []}]}}
    [""
     {:get {:summary "List all tiers"
            :openapi {:operationId "ListTiers"}
            :responses {200 {:body [:ref "TierList"]}}
            :handler queries/list-tiers}}]]])
