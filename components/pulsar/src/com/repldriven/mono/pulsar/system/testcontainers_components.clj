(ns com.repldriven.mono.pulsar.system.testcontainers-components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.testcontainers.containers ContainerLaunchException
                                          PulsarContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-broker-port 6650)
(def default-exposed-broker-http-port 8080)
(def default-exposed-ports [default-exposed-broker-port,
                            default-exposed-broker-http-port])
(def default-docker-image-name "apachepulsar/pulsar:latest")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-ports]} config]
           (try
             (let [container (-> (DockerImageName/parse docker-image-name)
                                  (.asCompatibleSubstituteFor "apachepulsar/pulsar")
                                  (PulsarContainer.))]
               (some-> (tc/init {:container container
                                 :exposed-ports exposed-ports})
                       (tc/start!)))
             (catch ContainerLaunchException e
               (log/error "Failed to start pulsar container, %s" e)))))),
   :system/stop (fn [{:system/keys [instance]}] (tc/stop! instance)),
   :system/config {:docker-image-name default-docker-image-name,
                   :exposed-ports default-exposed-ports}})
