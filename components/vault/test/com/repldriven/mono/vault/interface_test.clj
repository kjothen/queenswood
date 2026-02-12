(ns com.repldriven.mono.vault.interface-test
  (:require
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.utility.interface :as utility]
    [com.repldriven.mono.vault.interface :as SUT]

    [clojure.string :as str]
    [clojure.test :as test :refer [deftest is testing]]))

(deftest vault-component-test
  (testing "Vault component should authenticate and read secrets"
    (let [sys (error/nom-> (env/config "classpath:vault/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [client (system/instance sys [:vault :client])
                vault-config (system/config sys :vault :container)
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
                     (utility/prop-seq->kw-map secret-props))))))))))
