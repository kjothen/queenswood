(ns com.repldriven.mono.bank-api.tier.routes
  (:require
    [com.repldriven.mono.bank-api.tier.handlers :as handlers]
    [com.repldriven.mono.bank-api.tier.queries :as queries]))

(def routes
  [["/tiers"
    {:openapi {:tags ["Tiers"] :security [{"adminAuth" []}]}}
    [""
     {:get {:summary "List all tiers"
            :openapi {:operationId "ListTiers"}
            :responses {200 {:body [:ref "TierList"]}}
            :handler queries/list-tiers}}]
    ["/{tier-type}"
     {:parameters {:path {:tier-type [:ref "TierType"]}}}
     [""
      {:get {:summary "Get a tier by type"
             :openapi {:operationId "GetTier"}
             :responses {200 {:body [:ref "Tier"]}}
             :handler queries/get-tier}
       :put {:summary "Replace a tier's policies and limits"
             :openapi {:operationId "ReplaceTier"}
             :parameters {:body [:ref "ReplaceTierRequest"]}
             :responses {200 {:body [:ref "Tier"]}}
             :handler handlers/replace-tier}}]]]])
