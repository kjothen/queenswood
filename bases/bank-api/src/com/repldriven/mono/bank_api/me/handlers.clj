(ns com.repldriven.mono.bank-api.me.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]))

(defn get-me
  "Return the authenticated User and their Memberships. The auth
  interceptor upserts the User on every authenticated request, so a
  successful verification guarantees the User row exists by the time
  this handler runs — no 404 path is needed. SPAs key their
  onboarding decision on `memberships.length === 0` rather than on
  status code."
  [request]
  (let [{:keys [auth]} request
        {:keys [user memberships]} auth]
    (if (not= :user (:principal-type auth))
      (errors/anomaly->response
       (error/unauthorized :auth/unauthenticated
                           {:message "Only user JWTs may call /v1/me"}))
      {:status 200 :body {:user user :memberships (or memberships [])}})))
