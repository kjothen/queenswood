(ns com.repldriven.mono.bank-api.onboarding.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-membership.interface :as memberships]
    [com.repldriven.mono.bank-organization.interface :as organizations]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(def ^:private default-status :organization-status-test)
(def ^:private default-tier "micro")
(def ^:private default-currencies ["GBP"])

(defn- run-onboard
  "Returns either the response body (on success) or an anomaly. The
  User row has already been upserted by the auth interceptor — this
  handler just provisions the org + membership."
  [txn user identity-provider audience organization-name]
  (let-nom> [existing (memberships/list-by-user txn (:user-id user))]
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
                 {:keys [organization client-secret]} org-result
                 {:keys [organization-id]} organization
                 membership (memberships/new-membership
                             txn
                             {:user-id (:user-id user)
                              :organization-id organization-id
                              :role :role-owner})]
        {:user user
         :organization (assoc organization :client-secret client-secret)
         :membership membership}))))

(defn onboard
  "First-sign-in onboarding: provisions a new customer Organization
  (default tier + currencies) and binds the signed-in user to it as
  the owner. The User row has already been created by the auth
  interceptor's upsert. Returns 409 if the user already belongs to
  an organization — the MVP is one user, one org."
  [request]
  (let [{:keys [record-db record-store identity-provider auth parameters
                audiences-by-status]}
        request
        {:keys [user]} auth
        {:keys [body]} parameters
        {:keys [organization-name]} body
        txn {:record-db record-db :record-store record-store}
        audience (get audiences-by-status default-status)
        result (run-onboard txn
                            user
                            identity-provider
                            audience
                            organization-name)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201 :body result})))
