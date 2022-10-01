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

(defn props->kw-map
  [props]
  (into {}
        (map (fn [kv]
               (let [[k v] (str/split kv #"=")]
                 [(keyword k) v])) props)))

(deftest development-test
  (testing "Developers should be able to start/stop a vault system from the REPL"
    (with-system [sys (SUT/configure-system (get-in @env/env [:system :vault]))]
      (let [client (system/instance sys [:vault :client])
            vault-config (system/config sys :vault :container)
            token (:vault-token vault-config)
            secret (:secret-in-vault vault-config)]
        (is (some? client))
        (is (some? (SUT/authenticate-client! client :token token)))
        (let [[mount path] (-> secret first (str/split #"/"))
              secret-props (-> secret rest)]
          (is (= (SUT/read-secret client mount path)
                 (props->kw-map secret-props))))))))

(comment
  (def system-config (SUT/configure-system (get-in @env/env [:system :vault])))
  (get-in system-config [:system/defs :vault :container :system/config :vault-token])
  (def running-system (system/start system-config))
  (def client (system/instance running-system [:vault :client]))
  (SUT/authenticate-client! client :token token)
  (SUT/read-secret client mount path))
