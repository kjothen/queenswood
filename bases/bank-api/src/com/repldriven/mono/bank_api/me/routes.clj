(ns com.repldriven.mono.bank-api.me.routes
  (:require
    [com.repldriven.mono.bank-api.me.examples :refer [MeNotOnboarded]]
    [com.repldriven.mono.bank-api.me.handlers :as handlers]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/me"
    {:openapi {:tags ["Me"] :security [{"bearerAuth" ["user"]}]}}
    [""
     {:get {:summary "Retrieve the authenticated user and their memberships"
            :openapi {:operationId "RetrieveMe"}
            :responses {200 {:body [:ref "Me"]}
                        404 (ErrorResponse [#'MeNotOnboarded])}
            :handler handlers/get-me}}]]])
