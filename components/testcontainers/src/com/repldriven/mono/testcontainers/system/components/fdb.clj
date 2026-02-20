(ns com.repldriven.mono.testcontainers.system.components.fdb
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clj-test-containers.core :as tc]
    [clojure.string :as str])
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
             (do (log/info "FDB container started successfully")
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

(defn- get-container-ip
  [^GenericContainer container]
  (try
    ;; Get the container ID and use docker inspect to get the real IP
    (let [container-id (.getContainerId container)
          process (->
                    (ProcessBuilder.
                     ["docker" "inspect" "--format"
                      "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}"
                      container-id])
                    .start)
          _ (.waitFor process)
          ip (-> process
                 .getInputStream
                 slurp
                 str/trim)]
      (log/info "Got container IP from docker inspect:" ip)
      ip)
    (catch Exception e
      (log/error "Failed to get container IP, falling back to host:"
                 (.getMessage e))
      (.getHost container))))

(def cluster-file-path
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [container-map (get config :container)
               ^GenericContainer fdb-container (get container-map :container)
               ;; Use container's internal IP and port to avoid canonical
               ;; port mismatch
               host (get-container-ip fdb-container)
               port default-exposed-port
               content (str "docker:docker@" host ":" port)
               tmp-file (doto (java.io.File/createTempFile "fdb" ".cluster")
                          .deleteOnExit)]
           (spit tmp-file content)
           (let [path (.getAbsolutePath tmp-file)]
             (log/info "FDB cluster file path:" path)
             (log/info "FDB cluster file content:" content)
             path))))
   :system/config {:container system/required-component}})
