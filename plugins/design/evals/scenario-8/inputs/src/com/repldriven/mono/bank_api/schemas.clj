(ns com.repldriven.mono.bank-api.schemas)

(def ::account
  [:map
   [:account-id :string]
   [:balance number?]])

;; TODO: ::widget schema — :widget-id string, :name string
