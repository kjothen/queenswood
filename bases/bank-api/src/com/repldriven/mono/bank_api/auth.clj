(ns com.repldriven.mono.bank-api.auth
  (:require
    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.bank-api-key.interface :as bank-api-key]
    [com.repldriven.mono.utility.interface :as util]

    [sieppari.context :as sc]

    [clojure.string :as str]))

(def ^:private api-key-cache (cache/create 60000))

(defn- extract-bearer
  [request]
  (some-> (get-in request [:headers "authorization"])
          (str/split #" " 2)
          (as-> parts (when (= "Bearer" (first parts)) (second parts)))))

(defn- verify-org-key
  [request key-secret]
  (let [key-hash (encryption/hash-token key-secret)
        {:keys [record-db record-store]} request
        api-key (cache/lookup api-key-cache
                              key-hash
                              #(bank-api-key/get-api-key {:record-db record-db
                                                          :record-store
                                                          record-store}
                                                         key-hash))]
    (when (and (map? api-key) (zero? (:revoked-at api-key 0)))
      {:role :org :organization-id (:organization-id api-key)})))

(def authenticate
  {:name ::authenticate
   :enter (fn [ctx]
            (let [request (:request ctx)
                  key-secret (extract-bearer request)
                  admin-api-key (:admin-api-key request)]
              (cond
               (nil? key-secret)
               ctx

               (encryption/bytes-equals? (util/str->bytes key-secret)
                                         (util/str->bytes admin-api-key))
               (assoc-in ctx
                [:request :auth]
                {:role :admin
                 :organization-id
                 (:internal-organization-id
                  request)})

               :else
               (if-let [auth (verify-org-key request key-secret)]
                 (assoc-in ctx [:request :auth] auth)
                 ctx))))})

(def ^:private scheme->roles {"adminAuth" #{:admin} "orgAuth" #{:org :admin}})

(def authorize
  {:name ::authorize
   :enter (fn [ctx]
            (let [request (:request ctx)
                  security (get-in request
                                   [:reitit.core/match :data
                                    :openapi :security])
                  schemes (into #{} (mapcat keys) security)]
              (if (empty? schemes)
                ctx
                (let [role (get-in request [:auth :role])
                      allowed (into #{} (mapcat scheme->roles) schemes)]
                  (cond
                   (nil? role)
                   (sc/terminate ctx
                                 {:status 401
                                  :headers
                                  {"content-type" "application/json"}
                                  :body {:title "UNAUTHORIZED"
                                         :type "auth/unauthenticated"
                                         :status 401
                                         :detail
                                         "Missing or invalid API key"}})
                   (not (allowed role))
                   (sc/terminate ctx
                                 {:status 403
                                  :headers
                                  {"content-type" "application/json"}
                                  :body {:title "FORBIDDEN"
                                         :type "auth/forbidden"
                                         :status 403
                                         :detail
                                         "Insufficient privileges"}})
                   :else
                   ctx)))))})
