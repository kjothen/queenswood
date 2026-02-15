(ns com.repldriven.mono.testcontainers.system.components.postgres
  (:require
    [com.repldriven.mono.log.interface :as log]

    [clj-test-containers.core :as tc])
  (:import
    (org.testcontainers.containers ContainerLaunchException
                                   PostgreSQLContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 5432)
(def default-docker-image-name "postgres:16.2")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-port]} config]
    (try (log/info "Starting postgres container")
         (when-let [container (-> (tc/init {:container (PostgreSQLContainer.
                                                        (DockerImageName/parse
                                                         docker-image-name))
                                            :exposed-ports [exposed-port]})
                                  (tc/start!))]
           container)
         (catch ContainerLaunchException e
           (log/error "Failed to start postgres container, %s" e)))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping postgres container")
                  (when (some? instance) (tc/stop! instance)))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-port default-exposed-port}})
