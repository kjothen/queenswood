(ns com.repldriven.mono.bank-onfido-adapter.system
  (:require
    [com.repldriven.mono.bank-onfido-adapter.commands :as commands]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private re-register-poll-ms 30000)

(defn- adapter-webhook-url
  [adapter-url]
  (str adapter-url "/webhooks/onfido/check-completed"))

(defn- register-webhook
  [onfido-url adapter-url]
  (http/request
   {:method :post
    :url (str onfido-url "/v3.6/webhooks")
    :headers {"Content-Type" "application/json"}
    :body (json/write-str {:url (adapter-webhook-url adapter-url)})}))

(defn- registered?
  "Polls the simulator's webhook list and returns true iff the
  adapter's URL is currently registered. Used by the periodic
  re-register loop to detect simulator state loss (its registry is
  in-memory; a simulator pod bounce wipes everything and the
  adapter-side registration silently disappears)."
  [onfido-url adapter-url]
  (let [res (http/request {:method :get
                           :url (str onfido-url "/v3.6/webhooks")
                           :headers {"Accept" "application/json"}})
        url (adapter-webhook-url adapter-url)]
    (and (map? res)
         (number? (:status res))
         (< (:status res) 400)
         (try (some (fn [w] (= url (get w "url")))
                    (some-> res
                            :body
                            json/read-str
                            (get "webhooks")))
              (catch Throwable _ false)))))

(defn- ok-response?
  [res]
  (and (map? res) (number? (:status res)) (< (:status res) 400)))

(defn- register-webhook-with-retry
  "Retries webhook registration with the Onfido (or simulator)
  endpoint. The two services start in parallel so registration
  often races against simulator readiness; without retry the
  adapter fails to register on the first attempt and the
  simulator silently has nowhere to deliver check.completed
  webhooks. Caps at ~2 minutes (24 attempts × 5s)."
  [onfido-url adapter-url]
  (loop [attempt 1]
    (let [res (register-webhook onfido-url adapter-url)]
      (cond
       (ok-response? res)
       (do (log/info "Registered Onfido webhook"
                     {:adapter adapter-url
                      :status (:status res)
                      :attempt attempt})
           res)

       (< attempt 24)
       (do (log/warn "Onfido webhook registration not ready; retrying"
                     {:onfido onfido-url
                      :attempt attempt
                      :status (:status res)
                      :error (when (error/anomaly? res) (error/kind res))})
           (Thread/sleep 5000)
           (recur (inc attempt)))

       :else
       (do (log/error "Onfido webhook registration gave up after 24 attempts"
                      {:onfido onfido-url :last res})
           res)))))

(defn- start-re-register-loop
  "Daemon thread that polls the simulator every `poll-ms` and
  re-registers the adapter's webhook if its URL has dropped from
  the simulator's registry. Covers the simulator-bounce silent-loss
  case: the simulator's webhook map is in-memory, so a restart
  drops every registration and callbacks would otherwise stop
  arriving with no error visible to the adapter. With the
  simulator-side URL dedup, re-registration is a cheap no-op when
  the URL is already present, so polling doesn't accumulate state.
  Daemon-typed so it doesn't keep the JVM alive past system stop;
  no explicit stop signal needed."
  [onfido-url adapter-url poll-ms]
  (doto (Thread.
         (fn []
           (while true
             (try (Thread/sleep poll-ms)
                  (when-not (registered? onfido-url adapter-url)
                    (log/warn
                     "Onfido webhook missing from simulator; re-registering"
                     {:onfido onfido-url :adapter adapter-url})
                    (let [res (register-webhook onfido-url adapter-url)]
                      (if (ok-response? res)
                        (log/info "Re-registered Onfido webhook"
                                  {:adapter adapter-url
                                   :status (:status res)})
                        (log/error "Onfido webhook re-registration failed"
                                   {:adapter adapter-url :res res}))))
                  (catch InterruptedException _ (throw (InterruptedException.)))
                  (catch Throwable t
                    (log/error
                     t
                     "Onfido webhook poll iteration threw; continuing"))))))
    (.setDaemon true)
    (.setName "onfido-webhook-re-register")
    (.start)))

(def ^:private registrar
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [onfido-url adapter-url]} config
               ready? (atom false)]
           (log/info "Registering Onfido webhook (async)"
                     {:onfido onfido-url :adapter adapter-url})
           ;; Run registration in a future so system/start returns
           ;; promptly. The returned `ready-fn` closure exposes the
           ;; atom to the API's /ready route via jetty-adapter
           ;; config; pods stay 503 until registration succeeds.
           ;; After the initial registration succeeds, kick off a
           ;; daemon thread that polls the simulator and re-registers
           ;; if our URL has dropped from its registry.
           (future
            (let [res (register-webhook-with-retry onfido-url adapter-url)]
              (when (ok-response? res)
                (reset! ready? true)
                (log/info "Onfido webhook registration complete; pod ready")
                (start-re-register-loop onfido-url
                                        adapter-url
                                        re-register-poll-ms))))
           (fn [] @ready?))))
   :system/config {:onfido-url system/required-component
                   :adapter-url system/required-component}
   :system/instance-schema fn?})

(def ^:private command-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->OnfidoCommandProcessor config)))
   :system/config {:schemas system/required-component
                   :onfido-url system/required-component}
   :system/instance-schema some?})

(system/defcomponents :onfido-adapter
                      {:registrar registrar
                       :command-processor command-processor})
