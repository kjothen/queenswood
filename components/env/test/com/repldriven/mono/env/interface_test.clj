(ns com.repldriven.mono.env.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :as test :refer [deftest is testing]]
            [com.repldriven.mono.env.interface :as SUT]))

(deftest env-test
  (testing "A non-zero port number in config is preserved, ie `:port #port 80` -> `:port 80`"
    (let [config (SUT/set-env! (io/resource "env/test-env.edn") :default)
          port (get-in config [:system :port])]
      (is (= 80 port))))

  (testing "A zero port number in config returns an available local port, eg `:port #port 0` -> `:port 62457`"
    (let [config (SUT/set-env! (io/resource "env/test-env.edn") :test)
          port (get-in config [:system :port])]
      (is (and (>= port 1024) (<= port 65535))))))
