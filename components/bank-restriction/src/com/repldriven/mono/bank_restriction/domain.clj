(ns com.repldriven.mono.bank-restriction.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-restrictions
  "Builds a Restrictions record map. opts contains
  :policies and :limits as vectors matching the proto
  shape."
  [owner-id organization-id {:keys [policies limits]}]
  (let [now (System/currentTimeMillis)]
    {:restrictions-id (encryption/generate-id "rst")
     :owner-id owner-id
     :organization-id (or organization-id owner-id)
     :policies (vec (or policies []))
     :limits (vec (or limits []))
     :created-at now
     :updated-at now}))
