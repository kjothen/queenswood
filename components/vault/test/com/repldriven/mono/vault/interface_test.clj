(ns com.repldriven.mono.vault.interface-test
  (:require [clojure.string :as str]
            [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.vault.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system]))

(use-fixtures :once
  (test-system/fixture "classpath:vault/test-application.yml" :test)
  (fn [f] (system/with-*sys* test-system/*sysdef* (f))))

(defn prop-seq->kw-map
  [props]
  (into {}
        (map (fn [kv]
               (let [[k v] (mapv str/trim (str/split kv #"="))]
                 [(keyword k) v]))
             props)))

(deftest development-test
  (testing
   "Developers should be able to start/stop a vault system from a REPL"
    (is (some? system/*sys*))
    (let [client (system/instance system/*sys* [:vault :client])
          vault-config (system/config system/*sys* :vault :container)
          token (:vault-token vault-config)
          secret (:secret-in-vault vault-config)]
      (is (some? client))
      (is (some? (SUT/authenticate-client! client :token token)))
      (let [[mount path] (-> secret
                             first
                             (str/split #"/"))
            secret-props (-> secret
                             rest)]
        (is (= (SUT/read-secret client mount path)
               (prop-seq->kw-map secret-props)))))))
