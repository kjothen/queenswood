(ns com.repldriven.mono.bank-tier.domain
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn new-tier
  "Creates a new Tier record map."
  [tier-type policies limits]
  (let [now (System/currentTimeMillis)]
    {:tier-type tier-type
     :policies (vec (or policies []))
     :limits (vec (or limits []))
     :created-at now
     :updated-at now}))

(defn policy
  "Returns the first policy matching capability, or
  anomaly if not found."
  [tier capability]
  (let [result (->> (:policies tier)
                    (filter #(= capability (:capability %)))
                    first)]
    (or result
        (error/fail :tier/policy-not-found
                    {:message "Policy not found"
                     :capability capability
                     :tier-type (:tier-type tier)}))))

(defn limit
  "Returns the first limit matching type and kind, or
  nil if not found."
  [tier limit-type kind]
  (->> (:limits tier)
       (filter #(= limit-type (:type %)))
       (filter #(= kind (:kind %)))
       first))
