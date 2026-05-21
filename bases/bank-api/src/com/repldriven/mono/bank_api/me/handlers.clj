(ns com.repldriven.mono.bank-api.me.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-organization.interface :as organizations]

    [com.repldriven.mono.error.interface :as error]))

(defn- enrich-with-org-name
  "Inject the organization's `:name` into each membership as
  `:organization-name` so SPAs can render an org-context kicker
  without a follow-up lookup. Silently skips memberships whose
  org-id doesn't resolve — defensive only, since the membership row
  references a real org."
  [txn memberships]
  (mapv (fn [m]
          (let [org (organizations/get-organization
                     txn
                     (:organization-id m))]
            (cond-> m
                    (not (error/anomaly? org))
                    (assoc :organization-name (:name org)))))
        memberships))

(defn get-me
  "Return the authenticated User and their Memberships. The auth
  interceptor upserts the User on every authenticated request, so a
  successful verification guarantees the User row exists by the time
  this handler runs — no 404 path is needed. SPAs key their
  onboarding decision on `memberships.length === 0` rather than on
  status code.

  Each membership is enriched with `:organization-name` so the SPA
  can render an org-context kicker without a follow-up lookup."
  [request]
  (let [{:keys [record-db record-store auth]} request
        {:keys [user memberships]} auth
        txn {:record-db record-db :record-store record-store}]
    (if (not= :user (:principal-type auth))
      (errors/anomaly->response
       (error/unauthorized :auth/unauthenticated
                           {:message "Only user JWTs may call /v1/me"}))
      {:status 200
       :body {:user user
              :memberships (enrich-with-org-name
                            txn
                            (or memberships []))}})))
