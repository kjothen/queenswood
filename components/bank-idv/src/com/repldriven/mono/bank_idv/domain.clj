(ns com.repldriven.mono.bank-idv.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-idv
  "Creates a new IDV map with status pending."
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
  "Returns IDV with status accepted."
  [idv]
  (assoc idv
         :status :idv-status-accepted
         :updated-at (System/currentTimeMillis)))
