(ns com.repldriven.mono.testcontainers.system.components.keycloak
  "Keycloak testcontainer that boots a real Keycloak realm for
  high-fidelity auth tests. Wrapper around dasniko/testcontainers-
  keycloak — the lib handles startup probing and the
  `withRealmImportFile` import hook. The container exposes its
  randomly-mapped HTTP port; the `auth-server-url` component
  surfaces the full base URL the `keycloak/identity-provider`
  component can be pointed at."
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (dasniko.testcontainers.keycloak KeycloakContainer)))

(def default-docker-image-name "quay.io/keycloak/keycloak:26.0")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name realm-import-file realm-import-files
                       host-port]}
               config
               ;; Singular `:realm-import-file` stays supported for
               ;; back-compat; `:realm-import-files` (vector) wins when
               ;; both are set, so a system YAML can mount multiple
               ;; realms into the same container.
               files (or (seq realm-import-files)
                         (when realm-import-file [realm-import-file]))]
           (log/info "Starting keycloak container" docker-image-name)
           (let [c (KeycloakContainer. docker-image-name)]
             (doseq [f files] (.withRealmImportFile c f))
             ;; Fixed `host-port` pins :8080 to a known host port so a
             ;; host-running SPA can reach it; nil/0 keeps the random
             ;; mapping parallel test runs need.
             (when (and host-port (pos? host-port))
               (.setPortBindings c [(str host-port ":8080")]))
             (.start c)
             {:container c}))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when-let [c (:container instance)]
                    (log/info "Stopping keycloak container")
                    (.stop c)))
   :system/config {:docker-image-name default-docker-image-name
                   :realm-import-file nil
                   :realm-import-files nil
                   :host-port nil}
   :system/instance-schema map?})

(def auth-server-url
  "Resolves to the container's `<scheme>://<host>:<port>` base URL.
  Pair with `keycloak/identity-provider`'s `:base-url` config."
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [^KeycloakContainer c (:container (:container
                                                               config))]
                         (.getAuthServerUrl c))))
   :system/config {:container system/required-component}
   :system/instance-schema string?})
