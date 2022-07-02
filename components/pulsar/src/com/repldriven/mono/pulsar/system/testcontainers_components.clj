(ns com.repldriven.mono.pulsar.system.testcontainers-components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.testcontainers.containers ContainerLaunchException PulsarContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-broker-port 6650)
(def default-exposed-broker-http-port 8080)
(def default-exposed-ports [default-exposed-broker-port, default-exposed-broker-http-port])
(def default-docker-image-name "apachepulsar/pulsar:latest")

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [docker-image-name exposed-ports]} config]
                         (try
                           (some-> (tc/init {:container (-> (DockerImageName/parse docker-image-name)
                                                          (.asCompatibleSubstituteFor "apachepulsar/pulsar")
                                                          (PulsarContainer.)),
                                             :exposed-ports exposed-ports})
                             (tc/start!))
                           (catch ContainerLaunchException e
                             (log/error "Failed to start pulsar container, %s" e)))))),
   :system/stop (fn [{:system/keys [instance]}] (tc/stop! instance)),
   :system/config {:docker-image-name default-docker-image-name,
                   :exposed-ports default-exposed-ports}})

(def container-service-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [container-mapped-ports exposed-port]} config
                             service-url-str (str "pulsar://localhost:" (get container-mapped-ports exposed-port))]
                         (log/info "Mapped pulsar service-url:" service-url-str)
                         service-url-str))),
   :system/config {:container-mapped-ports system/required-component,
                   :exposed-port default-exposed-broker-port}})

(def container-service-http-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [container-mapped-ports exposed-port]} config
                             service-http-url-str (str "http://localhost:" (get container-mapped-ports exposed-port))]
                         (log/info "Mapped pulsar service-http-url:" service-http-url-str)
                         service-http-url-str))),
   :system/config {:container-mapped-ports system/required-component,
                   :exposed-port default-exposed-broker-http-port}})
