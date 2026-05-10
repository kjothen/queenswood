(ns com.repldriven.mono.bank-policy.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-policy
  [data]
  (let [{:keys [name
                category
                capabilities
                limits
                description
                enabled
                labels]
         :or {capabilities [] limits [] enabled true labels {}}}
        data
        now (utility/now)]
    (utility/assoc-some
     {:policy-id (utility/generate-id "pol")
      :name name
      :category category
      :capabilities capabilities
      :limits limits
      :labels labels
      :enabled enabled
      :created-at now
      :updated-at now}
     :description
     description)))

(defn new-binding
  [data]
  (let [{:keys [policy-id target reason]} data
        now (utility/now)]
    (utility/assoc-some
     {:binding-id (utility/generate-id "bnd")
      :policy-id policy-id
      :target target
      :created-at now}
     :reason
     reason)))
