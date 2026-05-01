(ns ^:eftest/synchronized
    com.repldriven.mono.bank-scenario-runner.interface-test
  (:require
    [com.repldriven.mono.bank-scenario-runner.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(defn- internal-account
  [sys]
  (-> (system/instance sys [:organizations :internal])
      (get-in [:organization :accounts 0 :account-id])))

(defn- scenario-files
  []
  (->> (io/file (.getFile (io/resource "bank-scenario-runner/scenarios")))
       (.listFiles)
       (filter (fn [f] (.endsWith (.getName f) ".edn")))
       (sort-by (fn [f] (.getName f)))))

(deftest scenarios-test
  (let [files (scenario-files)]
    (is (seq files) "expected scenarios on the classpath")
    (doseq [f files]
      (with-test-system
       [sys "classpath:bank-scenario-runner/application-test.yml"]
       (let [resource-path (str "bank-scenario-runner/scenarios/" (.getName f))]
         (nom-test> [loaded (SUT/from-resource resource-path)
                     _ (testing (:name loaded)
                         (SUT/run-commands (SUT/fresh-context (fdb-config sys)
                                                              (internal-account
                                                               sys))
                                           (SUT/steps loaded)))]))))))
