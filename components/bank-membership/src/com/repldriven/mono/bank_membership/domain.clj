(ns com.repldriven.mono.bank-membership.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-membership
  "Build a Membership record. MVP only ever creates `:role-owner`
  (the user creating an org IS its owner); the other roles
  (admin/developer/viewer) are reserved in the proto for later."
  [{:keys [user-id organization-id role]}]
  (let [now (utility/now)]
    {:membership-id (utility/generate-id "mem")
     :user-id user-id
     :organization-id organization-id
     :role (or role :role-owner)
     :created-at now
     :updated-at now}))
