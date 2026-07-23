(ns com.repldriven.queenswood.policy.capability
  (:require
    [com.repldriven.queenswood.policy.domain :as domain]
    [com.repldriven.queenswood.policy.match :as match]

    [com.repldriven.mono.error.interface :as error]))

(defn check
  [policies kind request]
  (let [matching (->> policies
                      (filter domain/live?)
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
