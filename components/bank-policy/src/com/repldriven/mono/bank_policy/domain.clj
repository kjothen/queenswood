(ns com.repldriven.mono.bank-policy.domain
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as utility]))

(defn live?
  "Whether a policy participates in evaluation. `:enabled` is the
  reversible pause; `:status` is the lifecycle — an archived policy is
  permanently out of evaluation. An unset/`:policy-status-unknown`
  status counts as active."
  [policy]
  (and (:enabled policy)
       (not= :policy-status-archived (:status policy))))

(defn new-policy
  [data]
  (let [{:keys [policy-id
                name
                category
                capabilities
                limits
                description
                enabled
                labels]
         :or {capabilities [] limits [] enabled true labels {}}}
        data
        now (utility/now)]
    ;; A supplied :policy-id makes the resulting save idempotent --
    ;; seed data (e.g. the bootstrap-service's platform / micro
    ;; restricted policies loaded from YAML) carries a stable id so
    ;; subsequent bootstrap runs upsert the same row instead of
    ;; piling up duplicates. Runtime-created policies pass no id and
    ;; get a freshly generated one.
    (utility/assoc-some
     {:policy-id (or policy-id (utility/generate-id "pol"))
      :name name
      :category category
      :capabilities capabilities
      :limits limits
      :labels labels
      :enabled enabled
      :status :policy-status-active
      :created-at now
      :updated-at now}
     :description
     description)))

(defn archive
  "Transition a policy to the archived lifecycle state. Rejects when
  the policy still has `bindings` — archival is for a policy no longer
  bound to anything, so the operator unbinds first."
  [policy bindings]
  (if (seq bindings)
    (error/reject :policy/still-bound
                  {:message "Cannot archive a policy that is still bound"
                   :policy-id (:policy-id policy)
                   :binding-count (count bindings)})
    (assoc policy
           :status :policy-status-archived
           :updated-at (utility/now))))

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
