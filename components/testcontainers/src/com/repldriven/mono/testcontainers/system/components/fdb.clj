(ns com.repldriven.mono.testcontainers.system.components.fdb
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (earth.adi.testcontainers.containers FoundationDBContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-docker-image-name "foundationdb/foundationdb:7.3.27")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name]} config]
    (try (log/info "Starting FDB container with image:" docker-image-name)
         (let [container (FoundationDBContainer. (DockerImageName/parse
                                                  docker-image-name))]
           (.start container)
           (log/info "FDB container started successfully")
           container)
         (catch Exception e
           (log/error "Failed to start FDB container:" (.getMessage e))
           (.printStackTrace e)
           nil))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping FDB container")
                  (when (some? instance) (.stop instance)))
   :system/config {:docker-image-name default-docker-image-name}})

(def cluster-file-path
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [container]} config
                             path (.getClusterFilePath container)]
                         (log/info "FDB cluster file path:" path)
                         path)))
   :system/config {:container system/required-component}})
