(ns com.repldriven.mono.testcontainers.system.components.fdb
  (:require
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.time Duration)
    (earth.adi.testcontainers.containers FoundationDBContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-docker-image-name "foundationdb/foundationdb:7.3.27")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name]} config]
    (log/info "Starting FDB container with image:" docker-image-name)
    (doto (-> (DockerImageName/parse docker-image-name)
              (FoundationDBContainer.))
      (.withStartupTimeout (Duration/ofSeconds 60))
      (.start))
    ;; FoundationDB container is returned directly (not wrapped in
    ;; a map) because the fdb component's system/components.clj
    ;; extracts the cluster file path from the running instance.
  ))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping FDB container")
                  (when (some? instance) (.stop instance)))
   :system/config {:docker-image-name default-docker-image-name}
   :system/config-schema [:map [:docker-image-name string?]]
   :system/instance-schema some?})
