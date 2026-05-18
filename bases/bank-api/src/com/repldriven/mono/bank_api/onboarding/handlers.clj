(ns com.repldriven.mono.bank-api.onboarding.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-membership.interface :as memberships]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.bank-user.interface :as users]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(def ^:private default-status :organization-status-test)
(def ^:private default-tier "micro")
(def ^:private default-currencies ["GBP"])

(defn- claims->identity-provider
  "Map Keycloak's `identity_provider` claim to the proto enum.
  The claim is present only when the user came through a federation
  broker (`google`, `github`, ...). When it's absent the user
  authenticated against Keycloak's own user store via username /
  password (the dev user does this), so default to
  `:identity-provider-password`. We deliberately avoid
  `:identity-provider-unknown` here: that's the proto enum's zero
  value and protojure's encoder skips zero-valued fields on the
  wire, leading to `parseFrom` rejecting the message for missing
  the required `identity_provider` field."
  [claims]
  (case (:identity_provider claims)
    "google" :identity-provider-google
    "github" :identity-provider-github
    :identity-provider-password))

(defn- claims->user-claims
  "Project Keycloak JWT claims into the shape `bank-user/upsert-by-
  keycloak-sub` expects. Pulls `name` from the `name` claim, falling
  back to a `given_name + family_name` concatenation when only the
  parts are present (some IdPs populate only one or the other)."
  [claims]
  {:keycloak-sub (:sub claims)
   :email (:email claims)
   :name (or (:name claims)
             (let [g (:given_name claims)
                   f (:family_name claims)]
               (cond (and g f)
                     (str g " " f)
                     g
                     g
                     f
                     f
                     :else
                     (:preferred_username claims))))
   :avatar-url (:picture claims)
   :identity-provider (claims->identity-provider claims)})

(defn- run-onboard
  "Returns either the response body (on success) or an anomaly
  (on any failure, including the explicit `already-exists`
  rejection). The wrapping handler converts the anomaly to an
  HTTP response."
  [txn claims organization-name identity-provider audience]
  (let-nom> [user (users/upsert-by-keycloak-sub txn
                                                (claims->user-claims claims))
             existing (memberships/list-by-user txn (:user-id user))]
    (if (seq existing)
      (error/reject :membership/already-exists
                    {:message "User already belongs to an organization"
                     :user-id (:user-id user)
                     :organization-id (:organization-id (first existing))})
      (let-nom> [org-result (organizations/new-organization
                             txn
                             organization-name
                             :organization-type-customer
                             default-status
                             default-tier
                             default-currencies
                             {:identity-provider identity-provider
                              :audience audience})
                 membership (memberships/create-membership
                             txn
                             {:user-id (:user-id user)
                              :organization-id (get-in org-result
                                                       [:organization
                                                        :organization-id])
                              :role :role-owner})]
        {:user user
         :organization (assoc (:organization org-result)
                              :client-secret
                              (:client-secret org-result))
         :membership membership}))))

(defn onboard
  "First-sign-in onboarding: upserts the User from the verified JWT,
  provisions a new customer Organization (default tier + currencies),
  and binds the user to it as the owner. Returns 409 if the user
  already belongs to an organization — the MVP is one user, one org."
  [request]
  (let [{:keys [record-db record-store identity-provider auth parameters
                audiences-by-status]}
        request
        {:keys [claims]} auth
        {:keys [body]} parameters
        {:keys [organization-name]} body
        txn {:record-db record-db :record-store record-store}
        audience (get audiences-by-status default-status)
        result (run-onboard txn
                            claims
                            organization-name
                            identity-provider
                            audience)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201 :body result})))
