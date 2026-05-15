(ns com.repldriven.mono.bank-test-api-scenarios.verbs
  (:require
    [com.repldriven.mono.bank-test-api-scenarios.refs :as refs]

    [com.repldriven.mono.bank-test-identity-provider.interface
     :as test-idp]
    [com.repldriven.mono.http-client.interface :as http]

    [matcher-combinators.matchers :as m]
    [matcher-combinators.standalone :as standalone]

    [clojure.data.json :as json]
    [clojure.test :refer [is]]
    [clojure.walk :as walk]))

(def ^:private matcher-constructors
  "EDN `:m/<name>` markers → matcher-combinators constructors.

  Maps that appear in expected position embed by default (sub-map
  semantics); vectors must match length and order; scalars compare
  with `=`. Markers below switch to richer matchers."
  {:m/equals (fn [v] (m/equals v))
   :m/embeds (fn [v] (m/embeds v))
   :m/nested-equals (fn [v] (m/nested-equals v))
   :m/regex (fn [p] (m/regex (re-pattern p)))
   :m/in-any-order (fn [coll] (m/in-any-order coll))
   :m/set-equals (fn [coll] (m/set-equals coll))
   :m/set-embeds (fn [coll] (m/set-embeds coll))
   :m/seq-of (fn [mm] (m/seq-of mm))
   :m/prefix (fn [coll] (m/prefix coll))
   :m/absent (fn [] m/absent)
   :m/any-of (fn [& ms] (apply m/any-of ms))
   :m/all-of (fn [& ms] (apply m/all-of ms))
   :m/mismatch (fn [mm] (m/mismatch mm))
   :m/within-delta (fn [d v] (m/within-delta d v))
   :m/any (fn [] (m/pred any?))
   :m/non-empty (fn [] (m/pred seq))})

(defn- matcher-marker?
  [x]
  (and (vector? x)
       (keyword? (first x))
       (= "m" (namespace (first x)))))

(defn- expand-marker
  [[tag & args]]
  (if-let [ctor (get matcher-constructors tag)]
    (apply ctor args)
    (throw (ex-info "Unknown matcher marker"
                    {:tag tag
                     :args args
                     :known (sort (keys matcher-constructors))}))))

(defn- expand-matchers
  [form]
  (walk/postwalk
   (fn [x] (if (matcher-marker? x) (expand-marker x) x))
   form))

(defn- resolve-auth
  [{:keys [admin-api-key captures]} auth]
  (cond
   (nil? auth)
   nil
   (= :admin auth)
   admin-api-key
   ;; A keyword references a previously-captured token (minted by
   ;; `:auth/mint-token` and stored via `:as`).
   (keyword? auth)
   (get captures auth)
   :else
   auth))

(defn- substitute-path
  [path path-params]
  (reduce-kv (fn [p k v] (.replace ^String p (str "{" (name k) "}") (str v)))
             path
             (or path-params {})))

(defn- header-name
  [k]
  (if (keyword? k) (name k) k))

(defn- merge-headers
  [base extra]
  (reduce-kv (fn [m k v] (assoc m (header-name k) v)) base (or extra {})))

(defn- absolute-url?
  [s]
  (and (string? s)
       (or (.startsWith ^String s "http://")
           (.startsWith ^String s "https://"))))

(defn- resolve-url
  [base-url url path path-params]
  (cond
   (absolute-url? url)
   url
   (some? url)
   (str base-url url)
   :else
   (str base-url (substitute-path path path-params))))

(defn- build-request
  [{:keys [base-url] :as ctx}
   {:keys [method url path path-params query-params body auth headers]}]
  (let [token (resolve-auth ctx auth)
        base-headers (cond-> {}
                             body
                             (assoc "Content-Type" "application/json")
                             token
                             (assoc "Authorization" (str "Bearer " token)))]
    (cond-> {:method method
             :url (resolve-url base-url url path path-params)
             :headers (merge-headers base-headers headers)}
            query-params
            (assoc :query-params query-params)
            body
            (assoc :body (json/write-str body)))))

(defn- assert-match
  [expected actual label]
  (let [matcher (expand-matchers expected)
        ok? (standalone/match? matcher actual)]
    (is ok?
        (when-not ok?
          (str label
               " mismatch:\n"
               (with-out-str
                 (standalone/print! (standalone/match matcher actual))))))))

(defmulti dispatch
  "Scenario step dispatch. `:api/*` methods drive the bank API over
  HTTP; `:assert/*` methods check the previous response."
  (fn [_ctx command] (:command command)))

(defmethod dispatch :api/request
  [{:keys [captures] :as ctx} {:keys [request as] :as step}]
  (let [resolved (refs/resolve-all captures request)
        res (http/request (build-request ctx resolved))
        body (http/res->edn res)
        response {:status (:status res) :body body :headers (:headers res)}
        ctx' (cond-> (assoc ctx :last-response response)
                     as
                     (assoc-in [:captures as] body))]
    (if-let [expect (:assert step)]
      (dispatch ctx' {:command :assert/response :assert expect})
      ctx')))

(defmethod dispatch :wait
  [ctx {:keys [duration-ms]}]
  (Thread/sleep ^long duration-ms)
  ctx)

(def ^:private default-poll-timeout-ms 10000)
(def ^:private default-poll-interval-ms 50)

(defmethod dispatch :api/poll
  [{:keys [captures] :as ctx} {:keys [request until timeout-ms interval-ms as]}]
  (let [resolved-request (refs/resolve-all captures request)
        until-matcher (expand-matchers (refs/resolve-all captures until))
        timeout (or timeout-ms default-poll-timeout-ms)
        interval (or interval-ms default-poll-interval-ms)
        deadline (+ (System/currentTimeMillis) timeout)]
    (loop [last-response nil]
      (let [res (http/request (build-request ctx resolved-request))
            body (http/res->edn res)
            response {:status (:status res) :body body :headers (:headers res)}]
        (cond
         (standalone/match? until-matcher response)
         (cond-> (assoc ctx :last-response response)
                 as
                 (assoc-in [:captures as] body))

         (>= (System/currentTimeMillis) deadline)
         (do (is false
                 (str "poll timed out after "
                      timeout
                      "ms waiting for response"
                      " to match\n  expected: " (pr-str until)
                      "\n  last actual: " (pr-str (or last-response response))))
             ctx)

         :else
         (do (Thread/sleep ^long interval)
             (recur response)))))))

(defmethod dispatch :assert/status
  [{:keys [last-response] :as ctx} {[expected] :args}]
  (is (= expected (:status last-response))
      (str "expected status " expected
           " got " (:status last-response)
           "; body: " (pr-str (:body last-response))))
  ctx)

(defmethod dispatch :assert/response
  [{:keys [captures last-response] :as ctx} {expectation :assert}]
  (let [{:keys [status body problem]} (refs/resolve-all captures expectation)
        actual-body (:body last-response)]
    (when status
      (is (= status (:status last-response))
          (str "expected status " status
               " got " (:status last-response)
               "; body: " (pr-str actual-body))))
    (when body (assert-match body actual-body "body"))
    (when problem
      (assert-match (merge {:type [:m/any] :title [:m/any]} problem)
                    actual-body
                    "problem-details")))
  ctx)

(defmethod dispatch :auth/mint-token
  [{:keys [identity-provider captures] :as ctx} {:keys [for as]}]
  (let [{:keys [client-id status roles audience] :or {roles [:org]}}
        (refs/resolve-all captures for)
        aud (or
             audience
             (if (= :live status) "queenswood-api-live" "queenswood-api-test"))
        token (test-idp/mint-token identity-provider
                                   {:azp client-id
                                    :sub client-id
                                    :aud [aud]
                                    :realm_access {:roles (mapv name roles)}})]
    (cond-> ctx
            as
            (assoc-in [:captures as] token))))
