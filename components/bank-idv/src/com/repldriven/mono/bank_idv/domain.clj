(ns com.repldriven.mono.bank-idv.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-idv
  [data]
  (let [{:keys [organization-id party-id]} data
        now (System/currentTimeMillis)]
    {:organization-id organization-id
     :party-id party-id
     :verification-id (utility/generate-id "idv")
     :status :idv-status-pending
     :created-at now
     :updated-at now}))

(defn accepted-idv
  [idv]
  (assoc idv
         :status :idv-status-accepted
         :completed-at (System/currentTimeMillis)
         :updated-at (System/currentTimeMillis)))

(defn rejected-idv
  [idv]
  (assoc idv
         :status :idv-status-rejected
         :completed-at (System/currentTimeMillis)
         :updated-at (System/currentTimeMillis)))
