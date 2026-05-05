(ns com.repldriven.mono.bank-clearbank-adapter.system
  (:require
    [com.repldriven.mono.bank-clearbank-adapter.commands
     :as commands]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private re-register-poll-ms 30000)

(defn- expected-url
  [webhook-url {:keys [path]}]
  (str webhook-url path))

(defn- register-webhook
  [simulator-url webhook-url {:keys [type path]}]
  (let [url (str webhook-url path)]
    (http/request
     {:method :post
      :url (str simulator-url "/v1/webhooks")
      :headers {"Content-Type" "application/json"}
      :body (json/write-str {:type type :url url})})))

(defn- ok-response?
  [res]
  (and (map? res) (number? (:status res)) (< (:status res) 400)))

(defn- register-webhook-with-retry
  "Retries a single webhook registration. Adapter and simulator
  start in parallel, so the first attempt may hit a not-yet-ready
  simulator and silently leave the webhook unregistered. Caps at
  ~2 minutes (24 attempts × 5s)."
  [simulator-url webhook-url {:keys [type] :as webhook}]
  (loop [attempt 1]
    (let [res (register-webhook simulator-url webhook-url webhook)]
      (cond
       (ok-response? res)
       (do (log/info "Registered ClearBank webhook"
                     {:type type :status (:status res) :attempt attempt})
           res)

       (< attempt 24)
       (do (log/warn "ClearBank webhook registration not ready; retrying"
                     {:type type
                      :simulator simulator-url
                      :attempt attempt
                      :status (:status res)
                      :error (when (error/anomaly? res) (error/kind res))})
           (Thread/sleep 5000)
           (recur (inc attempt)))

       :else
       (do (log/error "ClearBank webhook registration gave up after 24 attempts"
                      {:type type :simulator simulator-url :last res})
           res)))))

(defn- registered?
  "Polls the simulator's webhook list and returns true iff EVERY
  expected webhook (by `:type` and resolved URL) is currently
  present. Used by the periodic re-register loop to detect
  simulator state loss — its webhook map is in-memory, so a
  simulator restart drops every entry and callbacks would
  otherwise stop arriving."
  [simulator-url webhook-url webhooks]
  (let [res (http/request {:method :get
                           :url (str simulator-url "/v1/webhooks")
                           :headers {"Accept" "application/json"}})
        registered (try (some-> res
                                :body
                                json/read-str
                                (get "webhooks"))
                        (catch Throwable _ nil))
        present? (fn [{:keys [type] :as webhook}]
                   (some (fn [w]
                           (and (= type (get w "type"))
                                (= (expected-url webhook-url webhook)
                                   (get w "url"))))
                         registered))]
    (and (ok-response? res) (every? present? webhooks))))

(defn- re-register-missing
  [simulator-url webhook-url webhooks]
  (doseq [webhook webhooks]
    (let [res (register-webhook simulator-url webhook-url webhook)]
      (if (ok-response? res)
        (log/info "Re-registered ClearBank webhook"
                  {:type (:type webhook) :status (:status res)})
        (log/error "ClearBank webhook re-registration failed"
                   {:type (:type webhook) :res res})))))

(defn- start-re-register-loop
  "Daemon thread that polls the simulator every `poll-ms` and
  re-registers any of `webhooks` that are missing from its
  registry. The simulator stores webhooks keyed by
  `[sort-code type]` so re-POSTing is idempotent at storage level
  (no duplicates accumulate); we re-register the FULL set to
  recover cleanly from total state loss in one pass. Daemon-typed
  so it doesn't keep the JVM alive past system stop."
  [simulator-url webhook-url webhooks poll-ms]
  (doto (Thread.
         (fn []
           (while true
             (try
               (Thread/sleep poll-ms)
               (when-not (registered? simulator-url webhook-url webhooks)
                 (log/warn
                  "One or more ClearBank webhooks missing; re-registering all"
                  {:simulator simulator-url})
                 (re-register-missing simulator-url webhook-url webhooks))
               (catch InterruptedException _ (throw (InterruptedException.)))
               (catch Throwable t
                 (log/error
                  t
                  "ClearBank webhook poll iteration threw; continuing"))))))
    (.setDaemon true)
    (.setName "clearbank-webhook-re-register")
    (.start)))

(def ^:private registrar
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [simulator-url webhook-url webhooks]} config
               ready? (atom false)]
           (log/info "Registering ClearBank webhooks (async)"
                     {:simulator simulator-url :webhook webhook-url})
           ;; Run registration in a future so system/start returns
           ;; promptly. The returned `ready-fn` closure is wired
           ;; into the API ctx via jetty-adapter; /ready returns
           ;; 503 until every webhook registration succeeds. After
           ;; initial success, kick off a daemon thread that polls
           ;; the simulator and re-registers if any webhook drops.
           (future (let [results (doall (map (fn [webhook]
                                               (register-webhook-with-retry
                                                simulator-url
                                                webhook-url
                                                webhook))
                                             webhooks))]
                     (when (every? ok-response? results)
                       (reset! ready? true)
                       (log/info
                        "ClearBank webhook registration complete; pod ready")
                       (start-re-register-loop simulator-url
                                               webhook-url
                                               webhooks
                                               re-register-poll-ms))))
           (fn [] @ready?))))
   :system/config {:simulator-url system/required-component
                   :webhook-url system/required-component
                   :webhooks nil}
   :system/instance-schema fn?})

(def ^:private command-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->ClearBankCommandProcessor config)))
   :system/config {:schemas system/required-component
                   :clearbank-url system/required-component}
   :system/instance-schema some?})

(system/defcomponents :clearbank-adapter
                      {:registrar registrar
                       :command-processor command-processor})
