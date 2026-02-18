(ns com.repldriven.mono.testcontainers.system.components.pulsar
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clj-test-containers.core :as tc])
  (:import
    (org.testcontainers.containers ContainerLaunchException
                                   PulsarContainer)
    (org.testcontainers.utility DockerImageName)
    (java.time Duration)))

(def default-exposed-broker-port 6650)
(def default-exposed-broker-http-port 8080)

(def default-exposed-ports
  [default-exposed-broker-port default-exposed-broker-http-port])

(def default-docker-image-name "apachepulsar/pulsar:4.1.2")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-ports]} config]
    (try (log/info "Starting pulsar container")
         (let [container (-> (DockerImageName/parse docker-image-name)
                             (.asCompatibleSubstituteFor "apachepulsar/pulsar")
                             (PulsarContainer.))]
           (.addEnv container
                    "PULSAR_MEM"
                    "-Xms256m -Xmx256m -XX:MaxDirectMemorySize=256m")
           (.withStartupTimeout container (Duration/ofMinutes 1))
           (some-> (tc/init {:container container :exposed-ports exposed-ports})
                   (tc/start!)))
         (catch ContainerLaunchException e
           (log/error "Failed to start pulsar container, %s" e)))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping pulsar container")
                  (tc/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-ports default-exposed-ports}})

(def broker-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [container-map (get config :container)
                             ^PulsarContainer pulsar-container
                             (get container-map :container)
                             url (.getPulsarBrokerUrl pulsar-container)]
                         (log/info "Pulsar broker URL:" url)
                         url)))
   :system/config {:container system/required-component}})

(def http-service-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [container-map (get config :container)
                             ^PulsarContainer pulsar-container
                             (get container-map :container)
                             url (.getHttpServiceUrl pulsar-container)]
                         (log/info "Pulsar HTTP service URL:" url)
                         url)))
   :system/config {:container system/required-component}})
