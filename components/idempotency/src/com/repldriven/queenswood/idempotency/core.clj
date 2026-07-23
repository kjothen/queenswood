(ns com.repldriven.queenswood.idempotency.core
  (:require
    [com.repldriven.queenswood.idempotency.store :as store]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.edn :as edn]))

(def ^:private completed-ttl-ms (* 24 60 60 1000))    ; 24 hours
(def ^:private pending-ttl-ms (* 60 1000))          ; 60 seconds

(defn cacheable-status?
  "Cache definite outcomes (2xx, 4xx) — skip 5xx, which are
  transient and should be retried."
  [status]
  (and (integer? status) (< status 500)))

(defn- expired?
  [entry now]
  (>= now (:expires-at entry)))

(defn- stale-pending?
  "A `pending` entry older than `pending-ttl-ms` is treated as
  abandoned (e.g. handler crashed without releasing) and is
  reclaimable by a fresh request."
  [entry now]
  (>= now (+ (:created-at entry) pending-ttl-ms)))

(defn claim-or-replay
  "Single FDB transaction that atomically inspects the cache and
  decides what to do with a request:

  - `{:type ::completed :body <map> :status <int>}` — cached
    response should be replayed.
  - `{:type ::in-flight}` — another request with the same
    principal+operation+key is currently being processed; caller
    should return 409.
  - `{:type ::claimed}` — no live entry; we wrote a `pending`
    placeholder and the caller should run the handler.

  FDB's optimistic concurrency control serialises concurrent
  callers: if two threads both pass the lookup and try to write
  pending, one transaction will fail and FDB retries it; the retry
  sees the now-pending entry and returns `::in-flight`."
  [config principal-id operation idempotency-key]
  (fdb/transact
   config
   (fn [txn]
     (let [existing (store/lookup txn principal-id operation idempotency-key)
           now (utility/now)]
       (cond
        (and existing
             (= "completed" (:state existing))
             (not (expired? existing now)))
        {:type ::completed
         :status (:status existing)
         :body (edn/read-string (:body existing))}

        (and existing
             (= "pending" (:state existing))
             (not (stale-pending? existing now)))
        {:type ::in-flight}

        :else
        (do (store/save txn
                        {:principal-id principal-id
                         :operation operation
                         :idempotency-key idempotency-key
                         :state "pending"
                         :created-at now
                         :expires-at (+ now completed-ttl-ms)})
            {:type ::claimed}))))
   :idempotency/claim-or-replay
   "Failed to claim or replay idempotency entry"))

(defn complete
  "Replace the `pending` marker with a `completed` entry holding the
  response to replay. Called from the interceptor's `:leave` when the
  handler returned a cacheable status (2xx/4xx)."
  [config principal-id operation idempotency-key {:keys [status body]}]
  (let [now (utility/now)]
    (store/save config
                {:principal-id principal-id
                 :operation operation
                 :idempotency-key idempotency-key
                 :state "completed"
                 :status status
                 :body (pr-str body)
                 :created-at now
                 :expires-at (+ now completed-ttl-ms)})))

(defn release
  "Drop the `pending` claim — used when the response wasn't cacheable
  (5xx). Frees the slot so the next request can retry immediately
  rather than waiting out the stale-pending timeout."
  [config principal-id operation idempotency-key]
  (store/delete config principal-id operation idempotency-key))
