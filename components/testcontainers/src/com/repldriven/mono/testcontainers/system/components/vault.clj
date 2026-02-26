(ns com.repldriven.mono.testcontainers.system.components.vault
  (:require
    [com.repldriven.mono.log.interface :as log]

    [clj-test-containers.core :as tc]
    [clojure.string :as string])
  (:import
    (org.testcontainers.vault VaultContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-exposed-ports [8200])
(def default-docker-image-name "hashicorp/vault:1.21")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-ports vault-token
                       secret-in-vault init-commands]}
               config]
           (log/info "Starting vault container")
           (let [container (-> (DockerImageName/parse docker-image-name)
                               (.asCompatibleSubstituteFor "vault")
                               (VaultContainer.))]
             (when vault-token (.withVaultToken container vault-token))
             (when secret-in-vault
               (let [path (first secret-in-vault)
                     key-values (rest secret-in-vault)
                     kv-put-cmd (str "kv put " path
                                     " " (string/join " " key-values))]
                 (.withInitCommand container (into-array String [kv-put-cmd]))))
             (when init-commands
               (.withInitCommand container (into-array String init-commands)))
             (-> (tc/init {:container container :exposed-ports exposed-ports})
                 (tc/start!))))))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping vault container")
                  (tc/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-ports default-exposed-ports}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-ports [:vector int?]]]
   :system/instance-schema map?})
