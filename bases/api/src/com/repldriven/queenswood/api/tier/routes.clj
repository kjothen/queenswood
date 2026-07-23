(ns com.repldriven.queenswood.api.tier.routes
  (:require
    [com.repldriven.queenswood.api.tier.queries :as queries]))

(def routes
  [["/tiers"
    {:openapi {:tags ["Tiers"] :security [{"bearerAuth" ["admin"]}]}}
    [""
     {:get {:summary "List all tiers"
            :openapi {:operationId "ListTiers"}
            :responses {200 {:body [:ref "TierList"]}}
            :handler queries/list-tiers}}]]])
