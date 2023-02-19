(ns com.repldriven.mono.iam-service-account.spec)

(def ServiceAccount
  [:map
   [:name string?]
   [:project-id string?]
   [:unique-id string?]
   [:email string?]
   [:display-name string?]
   [:description string?]
   [:disabled boolean?]])
