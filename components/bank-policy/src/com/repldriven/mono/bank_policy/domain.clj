(ns com.repldriven.mono.bank-policy.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-policy
  "Builds a Policy map from input data. Generates policy-id
  and timestamps; fills defaults for optional repeated and
  boolean fields so the persisted record round-trips cleanly."
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
    (cond->
     {:policy-id (utility/generate-id "pol")
      :name name
      :category category
      :capabilities capabilities
      :limits limits
      :labels labels
      :enabled enabled
      :created-at now
      :updated-at now}

     description
     (assoc :description description))))

(defn new-binding
  "Builds a PolicyBinding map from input data. Generates
  binding-id and stamps created-at; pipes :policy-id,
  :target, and optional :reason through unchanged. `:target`
  is expected in the protojure nested-oneof shape
  `{:kind {:organization {:organization-id …}}}` etc."
  [data]
  (let [{:keys [policy-id target reason]} data
        now (utility/now)]
    (cond->
     {:binding-id (utility/generate-id "bnd")
      :policy-id policy-id
      :target target
      :created-at now}

     reason
     (assoc :reason reason))))
