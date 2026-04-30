(ns com.repldriven.mono.bank-policy.capability
  (:require
    [com.repldriven.mono.bank-policy.match :as match]

    [com.repldriven.mono.error.interface :as error]))

(defn check
  "Returns `true` when the requested capability `(kind, request)` is
  allowed by `policies`. Returns an `:unauthorized/policy-denied`
  anomaly otherwise.

  Disabled policies are skipped. A capability matches when its `:kind`
  variant equals `kind`, every top-level field equals the request's
  corresponding slot, and — if `:filters` is non-empty — at least one
  filter's set fields all agree with the request (unset fields inside
  a filter do not constrain). `:effect-deny` wins; otherwise at least
  one `:effect-allow` is required."
  [policies kind request]
  (let [matching (->> policies
                      (filter :enabled)
                      (mapcat :capabilities)
                      (filter (fn [c] (match/matches? c kind request))))
        denies (filter (fn [c] (= :effect-deny (:effect c))) matching)
        allows (filter (fn [c] (= :effect-allow (:effect c))) matching)]
    (cond
     (seq denies)
     (error/unauthorized :policy/denied
                         {:message (or (:reason (first denies))
                                       "Capability explicitly denied")
                          :kind kind
                          :request request})

     (seq allows)
     true

     :else
     (error/unauthorized :policy/denied
                         {:message "No matching allow capability"
                          :kind kind
                          :request request}))))
