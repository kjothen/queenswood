(ns com.repldriven.mono.testcontainers.system.components.testcontainers
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]

    [clj-test-containers.core :as tc])
  (:import
    (org.testcontainers.containers ContainerLaunchException)))

(def default-uri-scheme "http")
(def default-uri-host "localhost")
(def default-uri-path "")

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [docker-image-name exposed-ports]} config]
                         (try
                           (log/info "Starting" docker-image-name "container")
                           (-> (tc/create {:image-name docker-image-name
                                           :exposed-ports exposed-ports})
                               (tc/start!))
                           (catch ContainerLaunchException e
                             (log/error "Failed to start %s container, %s"
                                        docker-image-name
                                        e))))))
   :system/stop (fn [{:system/keys [config instance]}]
                  (log/info "Stopping" (:docker-image-name config) "container")
                  (tc/stop! instance))
   :system/config {:docker-image-name system/required-component
                   :exposed-ports system/required-component}})

(def mapped-ports
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [container]} config
                             ports (:mapped-ports container)]
                         (log/info "Container mapped ports:" ports)
                         ports)))
   :system/config {:container system/required-component}})

(def mapped-exposed-port
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [exposed-port container]} config
                             port (get-in container
                                          [:mapped-ports exposed-port])]
                         (log/info "Container mapped exposed port:" port)
                         port)))
   :system/config {:container system/required-component
                   :exposed-port system/required-component}})

(def uri
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [scheme host port path]} config
                             uri-str (str scheme "://" host ":" port path)]
                         (log/info "Mapped container uri:" uri-str)
                         uri-str)))
   :system/config {:scheme default-uri-scheme
                   :host default-uri-host
                   :port system/required-component
                   :path default-uri-path}})
