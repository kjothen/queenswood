(ns ^:eftest/synchronized
    com.repldriven.queenswood.test-api-scenarios.interface-test
  "Single-boot runner for EDN-defined API scenarios.

  Boots one bank-api system, then iterates every `.edn` file under
  `bank-test-api-scenarios/scenarios/` on the classpath. Each
  scenario gets its own runner context (fresh captures map) so
  scenarios cannot leak state into one another; the booted system
  is shared to amortise startup cost."
  (:require
    com.repldriven.queenswood.test-api-scenarios.system

    [com.repldriven.queenswood.test-api-scenarios.interface :as SUT]

    [com.repldriven.queenswood.api.api :as api]
    [com.repldriven.queenswood.clearbank-adapter.api :as cb-adapter]
    [com.repldriven.queenswood.clearbank-simulator.api :as cb-simulator]
    [com.repldriven.queenswood.onfido-adapter.api :as onfido-adapter]
    [com.repldriven.queenswood.onfido-simulator.api :as onfido-simulator]
    com.repldriven.queenswood.uk-companies-house-simulator.system
    [com.repldriven.queenswood.uk-companies-house-simulator.api :as
     ch-simulator]

    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]))

(defn- mint-admin-token
  "Exchange the seeded queenswood-admin client_credentials for an
  admin-roled service JWT against the queenswood realm. The
  service-account-queenswood-admin user has the `admin` realm role
  assigned in the test realm JSON, and the queenswood-admin client
  carries a realm-roles protocol mapper, so the minted token's
  `realm_access.roles` includes `admin` — bank-api's service-auth
  picks that up and grants the `:admin` role (admins carry no
  implicit `:bank-id`), giving the same principal shape as the
  legacy env-var admin bearer."
  [base-url]
  (let [res (http/request
             {:method :post
              :url (str base-url "/oauth/token")
              :headers {"content-type" "application/x-www-form-urlencoded"}
              :body (str "grant_type=client_credentials"
                         "&client_id=queenswood-admin"
                         "&client_secret=queenswood-admin-test-secret"
                         "&scope=queenswood-api-test+realm-roles")})
        body (http/res->edn res)]
    (or (:access_token body)
        ;; nosemgrep: no-raw-throw
        (throw (ex-info "Failed to mint scenario admin token"
                        {:status (:status res) :body body})))))

(defn- patch-handlers
  [defs]
  (-> defs
      (assoc-in [:system/defs :server :handler] api/app)
      (assoc-in [:system/defs :clearbank-simulator-server :handler]
                cb-simulator/app)
      (assoc-in [:system/defs :clearbank-adapter-server :handler]
                cb-adapter/app)
      (assoc-in [:system/defs :onfido-simulator-server :handler]
                onfido-simulator/app)
      (assoc-in [:system/defs :onfido-adapter-server :handler]
                onfido-adapter/app)
      (assoc-in [:system/defs :companies-house-simulator-server :handler]
                ch-simulator/app)))

(defn- scenario-files
  "Walk `bank-test-api-scenarios/scenarios/` recursively, returning
  `{:file File :relative \"<sub>/<name>.edn\"}` entries sorted by
  relative path so domain-grouped runs stay deterministic."
  []
  (let [root (io/file (.getFile (io/resource
                                 "bank-test-api-scenarios/scenarios")))
        prefix-len (inc (count (.getPath root)))]
    (->> (file-seq root)
         (filter (fn [f]
                   (and (.isFile ^java.io.File f)
                        (.endsWith (.getName f) ".edn"))))
         (map (fn [f]
                {:file f
                 :relative (subs (.getPath f) prefix-len)}))
         (sort-by :relative))))

(deftest api-scenarios-test
  ;; One test system serves every scenario. Per-scenario isolation
  ;; comes from a fresh runner context (own captures map), so
  ;; scenarios cannot read each other's state.
  (let [files (scenario-files)]
    (is (seq files) "expected scenarios on the classpath")
    (log/info "api scenarios starting" {:count (count files)})
    (with-test-system
     [sys
      ["classpath:bank-test-api-scenarios/application-test.yml"
       patch-handlers]]
     (let [jetty (system/instance sys [:server :jetty-adapter])
           base-url (server/http-local-url jetty)
           admin-token (mint-admin-token base-url)]
       (doseq [{:keys [relative]} files]
         (let [resource-path (str "bank-test-api-scenarios/scenarios/"
                                  relative)]
           (testing relative
             (nom-test> [loaded (SUT/from-resource resource-path)
                         _ (log/info "api scenario running"
                                     {:file relative
                                      :name (:name loaded)
                                      :steps (count (SUT/steps loaded))})
                         _ (SUT/run-scenario (SUT/fresh-context
                                              {:base-url base-url
                                               :admin-token admin-token
                                               :run-id (str (util/uuidv7))})
                                             resource-path)
                         _ (log/info "api scenario complete" {:file relative})]))))))))
