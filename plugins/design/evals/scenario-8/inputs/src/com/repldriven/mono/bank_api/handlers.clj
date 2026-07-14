(ns com.repldriven.mono.bank-api.handlers)

(defn get-account
  [request]
  {:status 200 :body {:account-id "acc.123" :balance 1000}})

;; TODO: get-widget handler
