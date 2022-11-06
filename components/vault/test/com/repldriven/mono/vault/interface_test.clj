(ns com.repldriven.mono.vault.interface-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test :refer :all]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.vault.interface :as SUT]
            [com.repldriven.mono.system.interface :as system :refer [with-system]]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "vault/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(defn prop-seq->kw-map
  [props]
  (into {} (map (fn [kv]
                  (let [[k v] (mapv str/trim (str/split kv #"="))]
                    [(keyword k) v])) props)))

(deftest development-test
  (testing "Developers should be able to start/stop a vault system from a REPL"
    (with-system [sys (SUT/configure-system (get-in @env/env [:system :vault]))]
      (is (some? sys))
      (let [client (system/instance sys [:vault :client])
            vault-config (system/config sys :vault :container)
            token (:vault-token vault-config)
            secret (:secret-in-vault vault-config)]
        (is (some? client))
        (is (some? (SUT/authenticate-client! client :token token)))
        (let [[mount path] (-> secret first (str/split #"/"))
              secret-props (-> secret rest)]
          (is (= (SUT/read-secret client mount path)
                (prop-seq->kw-map secret-props))))))))

(comment
  (env/set-env! (io/resource "vault/test-env.edn") :test)
  (def sys (-> (get-in @env/env [:system :vault])
               (SUT/configure-system)
               (system/start)))
  (def client (system/instance sys [:vault :client]))
  (def vault-config (system/config sys :vault :container))
  (def token (:vault-token vault-config))
  (def secret (:secret-in-vault vault-config))

  (SUT/authenticate-client! client :token token)
  (let [[mount path] (-> secret first (str/split #"/"))]
    (SUT/read-secret client mount path))
  (system/stop sys)
  )
