(ns com.repldriven.mono.pulsar.local-components
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
  {:start (fn [{:keys [docker-image-name exposed-ports]} instance _]
            (or instance
              (try
                (some-> (tc/init {:container (-> (DockerImageName/parse docker-image-name)
                                               (.asCompatibleSubstituteFor "apachepulsar/pulsar")
                                               (PulsarContainer.)),
                                  :exposed-ports exposed-ports})
                  (tc/start!))
                (catch ContainerLaunchException e
                  (log/error "Failed to start pulsar container, %s" e))))),
   :stop (fn [_ instance _] (tc/stop! instance)),
   :conf {:docker-image-name default-docker-image-name, :exposed-ports default-exposed-ports}})

(def service-url
  {:start (fn [{:keys [exposed-port container]} instance _]
            (or instance
              (let [service-url-str (str "pulsar://localhost:" (get-in container [:mapped-ports exposed-port]))]
                (log/info "Mapped pulsar service-url:" service-url-str)
                service-url-str))),
   :stop (fn [_ _ _]),
   :conf {:container system/required-component, :exposed-port default-exposed-broker-port}})

(def service-http-url
  {:start (fn [{:keys [exposed-port container]} instance _]
            (or instance
              (let [service-http-url-str (str "http://localhost:" (get-in container [:mapped-ports exposed-port]))]
                (log/info "Mapped pulsar service-http-url:" service-http-url-str)
                service-http-url-str))),
   :stop (fn [_ _ _]),
   :conf {:container system/required-component, :exposed-port default-exposed-broker-http-port}})
