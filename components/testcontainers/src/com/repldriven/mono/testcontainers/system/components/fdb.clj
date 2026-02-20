(ns com.repldriven.mono.testcontainers.system.components.fdb
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clj-test-containers.core :as tc])
  (:import
    (org.testcontainers.containers GenericContainer)
    (org.testcontainers.containers.wait.strategy Wait)
    (org.testcontainers.utility DockerImageName)
    (com.github.dockerjava.api.model ExposedPort
                                     Ports
                                     Ports$Binding)
    (java.time Duration)))

(def default-exposed-port 4500)
(def default-docker-image-name "foundationdb/foundationdb:7.3.27")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-port host-port]} config]
    (try (log/info "Starting FDB container on host port:" host-port)
         (let [container-port (ExposedPort/tcp exposed-port)
               port-bindings (doto (Ports.)
                               (.bind container-port
                                      (Ports$Binding/bindPort (int host-port))))
               container
               (-> (GenericContainer. (DockerImageName/parse docker-image-name))
                   (.withExposedPorts (into-array Integer [(int exposed-port)]))
                   ;; Critical: FDB_PORT tells FDB what port to advertise
                   ;; This must match the mapped port to avoid canonical
                   ;; port mismatch
                   (.withEnv "FDB_PORT" (str host-port))
                   ;; Use container mode (not host) so FDB binds to 0.0.0.0
                   (.withEnv "FDB_NETWORKING_MODE" "container")
                   (.withCreateContainerCmdModifier (reify
                                                     java.util.function.Consumer
                                                       (accept [_ cmd]
                                                         (-> cmd
                                                             .getHostConfig
                                                             (.withPortBindings
                                                              port-bindings)))))
                   (.waitingFor
                    (-> (Wait/forLogMessage ".*FDBD joined cluster.*\\n" 1)
                        (.withStartupTimeout (Duration/ofSeconds 60)))))]
           (if-let [started-container (some-> (tc/init {:container container})
                                              (tc/start!))]
             (do (log/info "FDB container started successfully on port:"
                           host-port)
                 ;; Give FDB a moment to fully initialize after joining
                 ;; cluster
                 (Thread/sleep 2000)
                 started-container)
             (do (log/error
                  "FDB container failed to start - no container returned")
                 nil)))
         (catch Exception e
           (log/error "Failed to start FDB container:" (.getMessage e))
           (.printStackTrace e)
           nil))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping FDB container")
                  (when (some? instance) (tc/stop! instance)))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-port default-exposed-port
                   :host-port system/required-component}})

(def cluster-file-path
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [host-port]} config
               ;; Use localhost with the mapped port since container IP is
               ;; not routable on macOS
               host "127.0.0.1"
               port host-port
               content (str "docker:docker@" host ":" port)
               tmp-file (doto (java.io.File/createTempFile "fdb" ".cluster")
                          .deleteOnExit)]
           (spit tmp-file content)
           (let [path (.getAbsolutePath tmp-file)]
             (log/info "FDB cluster file path:" path)
             (log/info "FDB cluster file content:" content)
             path))))
   :system/config {:host-port system/required-component}})
