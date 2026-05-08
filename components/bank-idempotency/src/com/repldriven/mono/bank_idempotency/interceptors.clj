(ns com.repldriven.mono.bank-idempotency.interceptors
  (:require
    [com.repldriven.mono.bank-idempotency.core :as core]

    [clojure.string :as str]
    [sieppari.context :as sc]))

(defn- principal-id
  "Compose a principal id from the auth context. Org-scoped requests
  use the api-key-id; admin-scoped requests share the literal `admin`
  (single shared scope, since admin keys aren't tracked individually)."
  [auth]
  (case (:role auth)
    :org (:api-key-id auth)
    :admin "admin"
    nil))

(defn- operation
  "Stable identifier for the route — METHOD + path-template, e.g.
  `POST /v1/cash-accounts`. Scopes the cache so the same key can be
  reused (independently) across different endpoints."
  [request]
  (let [method (some-> (:request-method request)
                       name
                       str/upper-case)
        path (get-in request [:reitit.core/match :template])]
    (when (and method path)
      (str method " " path))))

(defn- in-flight-response
  []
  {:status 409
   :body
   {:title "REJECTED"
    :type "mono/idempotent-request-in-flight"
    :status 409
    :detail
    "A request with this Idempotency-Key is already being processed; please retry in a moment."}})

(def cache-response
  "Idempotency cache with concurrent-request protection.

  `:enter` runs an atomic FDB check-and-set. Three outcomes:

  - completed → terminate with the cached `{:status :body}` replay
  - pending   → terminate with 409 (another request in flight)
  - claimed   → wrote a `pending` marker; let the handler run

  `:leave` finalises the claim:

  - cacheable status (2xx/4xx) → write `completed` entry
  - 5xx                        → drop the `pending` marker so the
                                  request can be retried immediately

  Place at the route level AFTER `server/require-idempotency-key`
  so the header is known valid; auth has already run by the time
  any route-level interceptor fires."
  {:name ::cache-response
   :enter (fn [ctx]
            (let [request (:request ctx)
                  {:keys [headers auth record-db record-store]} request
                  key (get headers "idempotency-key")
                  pid (principal-id auth)
                  op (operation request)]
              (if-not (and pid op key)
                ctx
                (let [config {:record-db record-db :record-store record-store}
                      result (core/claim-or-replay config pid op key)]
                  (case (:type result)
                    ::core/completed (sc/terminate ctx
                                                   {:status (:status result)
                                                    :body (:body result)})
                    ::core/in-flight (sc/terminate ctx (in-flight-response))
                    ::core/claimed
                    (-> ctx
                        (assoc-in [:request :idempotency/principal-id] pid)
                        (assoc-in [:request :idempotency/operation] op)
                        (assoc-in [:request :idempotency/key] key)))))))
   :leave (fn [ctx]
            (let [{:keys [record-db record-store]
                   :idempotency/keys [principal-id operation key]}
                  (:request ctx)
                  response (:response ctx)
                  config {:record-db record-db :record-store record-store}]
              (when (and principal-id operation key response)
                (if (core/cacheable-status? (:status response))
                  (core/complete config principal-id operation key response)
                  (core/release config principal-id operation key)))
              ctx))})
