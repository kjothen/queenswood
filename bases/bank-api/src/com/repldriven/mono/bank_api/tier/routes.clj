(ns com.repldriven.mono.bank-api.tier.routes
  (:require
    [com.repldriven.mono.bank-api.tier.examples :refer [TierNotFound]]
    [com.repldriven.mono.bank-api.tier.handlers :as handlers]
    [com.repldriven.mono.bank-api.tier.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/tiers"
    {:openapi {:tags ["Tiers"] :security [{"adminAuth" []}]}}
    [""
     {:get {:summary "List all tiers"
            :openapi {:operationId "ListTiers"}
            :responses {200 {:body [:ref "TierList"]}}
            :handler queries/list-tiers}
      :post {:summary "Create a new tier"
             :openapi {:operationId "CreateTier" :requestBody {:required true}}
             :parameters {:body [:ref "CreateTierRequest"]}
             :responses {201 {:body [:ref "Tier"]}}
             :handler handlers/create-tier}}]
    ["/{tier-id}"
     {:parameters {:path {:tier-id [:ref "TierId"]}}}
     [""
      {:get {:summary "Get a tier by id"
             :openapi {:operationId "GetTier"}
             :responses {200 {:body [:ref "Tier"]}
                         404 (ErrorResponse [#'TierNotFound])}
             :handler queries/get-tier}
       :put {:summary "Replace a tier's policies and limits"
             :openapi {:operationId "ReplaceTier" :requestBody {:required true}}
             :parameters {:body [:ref "ReplaceTierRequest"]}
             :responses {200 {:body [:ref "Tier"]}
                         404 (ErrorResponse [#'TierNotFound])}
             :handler handlers/replace-tier}}]]]])
