(ns com.repldriven.mono.testcontainers.system.components.postgres
  (:require
    [com.repldriven.mono.log.interface :as log]

    [clj-test-containers.core :as tc])
  (:import
    (java.time Duration)
    (org.testcontainers.containers PostgreSQLContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 5432)
(def default-docker-image-name "postgres:16.2")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-port]} config]
    (log/info "Starting postgres container")
    (let [container (PostgreSQLContainer. (DockerImageName/parse
                                           docker-image-name))]
      (.withStartupTimeout container (Duration/ofSeconds 60))
      (-> (tc/init {:container container :exposed-ports [exposed-port]})
          (tc/start!)))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping postgres container")
                  (when (some? instance) (tc/stop! instance)))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-port default-exposed-port}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-port int?]]
   :system/instance-schema map?})
