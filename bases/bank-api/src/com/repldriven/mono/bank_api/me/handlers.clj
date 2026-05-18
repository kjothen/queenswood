(ns com.repldriven.mono.bank-api.me.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]))

(defn get-me
  "Return the authenticated User and their Memberships. The auth
  interceptor has already resolved both from the JWT's Keycloak sub.
  Returns 404 when the JWT verified but no User record yet exists —
  the SPA uses that signal to redirect to onboarding."
  [request]
  (let [{:keys [auth]} request
        {:keys [user memberships]} auth]
    (cond
     (not= :user (:principal-type auth))
     (errors/anomaly->response
      (error/unauthorized :auth/unauthenticated
                          {:message "Only user JWTs may call /v1/me"}))

     (nil? user)
     {:status 404
      :body {:title "REJECTED"
             :type ":user/not-found"
             :status 404
             :detail (str "User signed in but has no Queenswood account yet"
                          " — POST /v1/onboarding/me first")}}

     :else
     {:status 200 :body {:user user :memberships (or memberships [])}})))
