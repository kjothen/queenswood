(ns com.repldriven.mono.bank-user.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-user
  "Build a User map from OIDC claims. Status defaults to active --
  Keycloak is the authority on identity; if Google authenticated
  the user, they are active by definition."
  [{:keys [keycloak-sub email name avatar-url identity-provider]}]
  (let [now (utility/now)]
    (cond-> {:user-id (utility/generate-id "usr")
             :keycloak-sub keycloak-sub
             :email email
             :name name
             :identity-provider (or identity-provider
                                    :identity-provider-unknown)
             :status :user-status-active
             :created-at now
             :updated-at now}
            avatar-url
            (assoc :avatar-url avatar-url))))

(defn apply-claims
  "Merge a fresh set of OIDC claims into an existing User -- used
  when a known Keycloak sub re-signs-in and their email / name /
  avatar may have changed. user-id, keycloak-sub, status, created-at
  stay put; updated-at refreshes."
  [user {:keys [email name avatar-url identity-provider]}]
  (cond-> (assoc user :updated-at (utility/now))
          email
          (assoc :email email)
          name
          (assoc :name name)
          avatar-url
          (assoc :avatar-url avatar-url)
          identity-provider
          (assoc :identity-provider identity-provider)))
