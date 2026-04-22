(ns com.repldriven.mono.bank-api.errors
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]

    [clojure.string :as str]))

(defn error-response
  "Builds an RFC 9457-shaped ErrorResponse body.

  Two-arity form derives fields from an anomaly.
  Three-arity form derives fields from a command response.
  Four-arity form takes explicit title, type, and detail."
  ([status anomaly]
   (cond-> {:title (cond (error/unauthorized? anomaly)
                         "UNAUTHORIZED"
                         (error/rejection? anomaly)
                         "REJECTED"
                         :else
                         "FAILED")
            :type (str (error/kind anomaly))
            :status status}
           (:message (error/payload anomaly))
           (assoc :detail
                  (:message (error/payload anomaly)))))
  ([status command-status result]
   (cond-> {:title command-status :type (:reason result) :status status}
           (:message result)
           (assoc :detail (:message result))))
  ([status title type detail]
   (cond-> {:title title :type type :status status}
           detail
           (assoc :detail detail))))

(def ^:private rejection-status-overrides
  "Explicit status overrides for rejection categories whose names
  don't fit the default heuristics."
  {:cash-account-product/no-draft 409
   :cash-account-product/no-published-version 404
   :interest/no-settlement 404})

(defn rejection-kind->status
  "Pick an HTTP status code for a rejection kind keyword.

  - explicit override wins, else
  - name ends with `not-found`             -> 404
  - name is `already-exists` / `exists`
    or contains `duplicate`                -> 409
  - anything else rejection                -> 422"
  [kind]
  (let [n (some-> kind
                  name)]
    (cond (contains? rejection-status-overrides kind)
          (get rejection-status-overrides kind)
          (str/ends-with? (or n "") "not-found")
          404
          (or (= n "already-exists")
              (= n "exists")
              (str/includes? (or n "") "duplicate"))
          409
          :else
          422)))

(defn anomaly->status
  "Pick an HTTP status code for an anomaly. Rejection anomalies
  flow through `rejection-kind->status`. Unauthorized -> 401.
  Error anomalies -> 500."
  [anomaly]
  (cond (error/unauthorized? anomaly)
        401
        (not (error/rejection? anomaly))
        500
        :else
        (rejection-kind->status (error/kind anomaly))))

(defn- log-500-anomaly
  "Log the underlying exception carried in an error anomaly so we can
  diagnose what blew up without surfacing the stack trace to clients."
  [anomaly]
  (let [{:keys [message exception]} (error/payload anomaly)]
    (if exception
      (log/error exception
                 (str (error/kind anomaly) ": " (or message "failed")))
      (log/error (str (error/kind anomaly) ": " (or message "failed"))))))

(defn anomaly->response
  "Map an anomaly to an RFC 9457 ring response via `anomaly->status`.
  Logs the full exception for any anomaly that maps to 500 so the cause
  is diagnosable even when only a terse message reaches the client."
  [anomaly]
  (let [status (anomaly->status anomaly)]
    (when (= 500 status) (log-500-anomaly anomaly))
    {:status status :body (error-response status anomaly)}))
