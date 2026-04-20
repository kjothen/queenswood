(ns com.repldriven.mono.bank-payee-check.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(def ^:private ttl-hours 24)

(defn- now-rfc3339
  []
  (str (java.time.Instant/now)))

(defn- expires-rfc3339
  [created-at]
  (str (.plus (java.time.Instant/parse created-at)
              (java.time.Duration/ofHours ttl-hours))))

(defn- sanitize-result
  [result]
  {:match-result (:match-result result)
   :actual-name (or (:actual-name result) "")
   :reason-code (or (:reason-code result) "")
   :reason (or (:reason result) "")})

(defn new-check
  "Builds a PayeeCheck map from an organization-id, request
  map, and result map. Generates check-id and timestamps."
  [organization-id request result]
  (let [created (now-rfc3339)]
    {:check-id (encryption/generate-id "chk")
     :organization-id organization-id
     :request request
     :result (sanitize-result result)
     :created-at created
     :expires-at (expires-rfc3339 created)}))
