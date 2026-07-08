(ns com.repldriven.mono.bank-party.domain
  (:refer-clojure :exclude [type])
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-party
  [data]
  (let [{:keys [bank-id type display-name]} data
        now (System/currentTimeMillis)
        status (if (= :party-type-person type)
                 :party-status-pending
                 :party-status-active)]
    {:bank-id bank-id
     :party-id (utility/generate-id "pty")
     :type type
     :display-name display-name
     :status status
     :created-at now
     :updated-at now}))

(defn activate-party
  [party]
  (assoc party
         :status :party-status-active
         :updated-at (System/currentTimeMillis)))

(defn reject-party
  [party]
  (assoc party
         :status :party-status-rejected
         :updated-at (System/currentTimeMillis)))

(defn new-party-national-identifier
  [national-identifier bank-id party-id]
  (let [{:keys [type value issuing-country]} national-identifier]
    {:bank-id bank-id
     :party-id party-id
     :type type
     :value value
     :issuing-country issuing-country
     :created-at (System/currentTimeMillis)}))

