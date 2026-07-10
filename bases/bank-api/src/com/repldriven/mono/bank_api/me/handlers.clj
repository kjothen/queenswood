(ns com.repldriven.mono.bank-api.me.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-bank-query.interface :as banks]

    [com.repldriven.mono.error.interface :as error]))

(defn- enrich-with-bank-name
  "Inject the bank's `:name` into each membership as `:bank-name`
  so SPAs can render a bank-context kicker without a follow-up
  lookup. Silently skips memberships whose bank-id doesn't resolve —
  defensive only, since the membership row references a real bank."
  [txn memberships]
  (mapv (fn [m]
          (let [b (banks/get-bank
                   txn
                   (:bank-id m))]
            (cond-> m
                    (not (error/anomaly? b))
                    (assoc :bank-name (:name b)))))
        memberships))

(defn get-me
  "Return the authenticated User and their Memberships. The auth
  interceptor upserts the User on every authenticated request, so a
  successful verification guarantees the User row exists by the time
  this handler runs — no 404 path is needed. SPAs key their
  onboarding decision on `memberships.length === 0` rather than on
  status code.

  Each membership is enriched with `:bank-name` so the SPA can
  render a bank-context kicker without a follow-up lookup."
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
              :memberships (enrich-with-bank-name
                            txn
                            (or memberships []))}})))
