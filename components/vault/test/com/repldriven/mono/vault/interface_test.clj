(ns com.repldriven.mono.vault.interface-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test :refer :all]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.vault.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "vault/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start/stop a vault system from the REPL"
    (let [system-config (SUT/configure-system (get-in @env/env [:system :vault]))]
      (try
        (let [running-system (system/start system-config)]
          (try
            (let [client (system/instance running-system [:vault :client])
                  token (get-in system-config
                                [:system/defs :vault :container
                                 :system/config :vault-token])
                  secret (get-in system-config
                                 [:system/defs :vault :container
                                  :system/config :secret-in-vault])]
              (is (some? client))
              (is (some? (SUT/authenticate-client! client :token token)))
              (let [mount (-> secret first (str/split #"/") first)
                    path (-> secret first (str/split #"/") second)
                    secret-kvs (-> secret rest)]
                (is (= (SUT/read-secret client mount path)
                       (into {} (map (fn [kv]
                                       (let [[k v] (str/split kv #"=")]
                                         [(keyword k) v]))) secret-kvs)))))
            (catch Exception e
              (assert false (format "Unable to get vault client, %s" e)))
            (finally
              (system/stop running-system))))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))))))


(comment
  (def system-config (SUT/configure-system (get-in @env/env [:system :vault])))
  (get-in system-config [:system/defs :vault :container :system/config :vault-token])
  (def running-system (system/start system-config))
  (def client (system/instance running-system [:vault :client]))
  (SUT/authenticate-client! client :token token)
  (SUT/read-secret client mount path))
