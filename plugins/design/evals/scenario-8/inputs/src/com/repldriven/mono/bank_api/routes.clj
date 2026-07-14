(ns com.repldriven.mono.bank-api.routes
  (:require
    [com.repldriven.mono.bank-api.handlers :as handlers]
    [com.repldriven.mono.bank-api.schemas :as schemas]))

(def routes
  ["/v1"
   ["/accounts/:id"
    {:get {:summary "Get an account"
           :handler handlers/get-account
           :security [{:api-key []}]
           :responses {200 {:body ::schemas/account
                             :description "The account"
                             :examples
                             {"default"
                              {:value {:account-id "acc.123"
                                       :balance 1000}}}}}}}]

   ;; TODO: GET /widgets/:id, following the accounts route's shape
   ])
