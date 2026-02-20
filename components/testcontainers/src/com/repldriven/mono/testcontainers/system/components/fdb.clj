(ns com.repldriven.mono.testcontainers.system.components.fdb
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clj-test-containers.core :as tc])
  (:import
    (org.testcontainers.containers ContainerLaunchException
                                   GenericContainer)
    (org.testcontainers.containers.wait.strategy Wait)
    (org.testcontainers.utility DockerImageName)
    (java.time Duration)))

(def default-exposed-port 4500)
(def default-docker-image-name "foundationdb/foundationdb:7.3.27")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-port]} config]
    (try (log/info "Starting FDB container")
         (let [container
               (-> (GenericContainer. (DockerImageName/parse docker-image-name))
                   (.withExposedPorts (into-array Integer [(int exposed-port)]))
                   (.waitingFor
                    (-> (Wait/forLogMessage ".*FDBD joined cluster.*\\n" 1)
                        (.withStartupTimeout (Duration/ofSeconds 60)))))]
           (when-let [started-container (some-> (tc/init {:container container
                                                          :exposed-ports
                                                          [exposed-port]})
                                                (tc/start!))]
             ;; Give FDB a moment to fully initialize after joining cluster
             (Thread/sleep 2000)
             started-container))
         (catch ContainerLaunchException e
           (log/error "Failed to start FDB container, %s" e)))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping FDB container")
                  (when (some? instance) (tc/stop! instance)))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-port default-exposed-port}})

(def cluster-file-path
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [container-map (get config :container)
               ^GenericContainer fdb-container (get container-map :container)
               host (.getHost fdb-container)
               port (.getMappedPort fdb-container (int default-exposed-port))
               content (str "docker:docker@" host ":" port)
               tmp-file (doto (java.io.File/createTempFile "fdb" ".cluster")
                          .deleteOnExit)]
           (spit tmp-file content)
           (let [path (.getAbsolutePath tmp-file)]
             (log/info "FDB cluster file path:" path)
             (log/info "FDB cluster file content:" content)
             path))))
   :system/config {:container system/required-component}})
