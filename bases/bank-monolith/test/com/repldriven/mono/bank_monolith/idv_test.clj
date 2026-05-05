(ns ^:eftest/synchronized com.repldriven.mono.bank-monolith.idv-test
  "End-to-end IDV chain — exercises the full party-pending →
  Onfido check → check.completed webhook → idv-completed event →
  party-active path with both real provider bases (simulator and
  adapter) running in-process. The Onfido simulator routes
  outcomes by applicant first_name (`Reject*` → consider, default
  → clear)."
  (:require
    com.repldriven.mono.bank-monolith.system

    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.bank-clearbank-adapter.api :as cb-adapter]
    [com.repldriven.mono.bank-clearbank-simulator.api :as cb-simulator]
    [com.repldriven.mono.bank-onfido-adapter.api :as onfido-adapter]
    [com.repldriven.mono.bank-onfido-simulator.api :as onfido-simulator]

    [com.repldriven.mono.bank-party.interface :as parties]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:private admin-api-key (System/getenv "MONO_ADMIN_API_KEY"))

(defn- patch-handlers
  [defs]
  (-> defs
      (assoc-in [:system/defs :clearbank-simulator-server :handler]
                cb-simulator/app)
      (assoc-in [:system/defs :clearbank-adapter-server :handler]
                cb-adapter/app)
      (assoc-in [:system/defs :onfido-simulator-server :handler]
                onfido-simulator/app)
      (assoc-in [:system/defs :onfido-adapter-server :handler]
                onfido-adapter/app)
      (assoc-in [:system/defs :server :handler] api/app)))

(defn- create-org
  [base-url org-name]
  (http/request {:method :post
                 :url (str base-url "/v1/organizations")
                 :headers {"Content-Type" "application/json"
                           "Authorization" (str "Bearer " admin-api-key)}
                 :body (json/write-str {"name" org-name
                                        "status" "live"
                                        "tier" "micro"
                                        "currencies" ["GBP"]})}))

(defn- create-party
  [base-url token first-name last-name]
  (http/request
   {:method :post
    :url (str base-url "/v1/parties")
    :headers {"Content-Type" "application/json"
              "Authorization" (str "Bearer " token)
              "Idempotency-Key" (str (util/uuidv7))}
    :body (json/write-str
           {"type" "person"
            "display-name" (str first-name " " last-name)
            "given-name" first-name
            "family-name" last-name
            "date-of-birth" "1950-07-27"
            "nationality" "GB"
            "national-identifier"
            {"type" "national-insurance"
             ;; Unique per call so per-org NI uniqueness
             ;; doesn't trip across multiple test cases.
             "value" (str "NI"
                          (format "%010d"
                                  (rand-int 1000000000)))
             "issuing-country" "GB"}})}))

(defn- poll-party-status
  "Polls the party until status matches `expected` or the timeout
  fires. Returns the last-seen party."
  [config organization-id party-id expected timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (let [party (parties/get-party config organization-id party-id)
            status (when-not (error/anomaly? party) (:status party))]
        (cond
         (= expected status)
         party
         (>= (System/currentTimeMillis) deadline)
         party
         :else
         (do (Thread/sleep 200) (recur)))))))

(deftest idv-clear-flow-test
  (with-test-system
   [sys ["classpath:bank-monolith/application-test.yml" patch-handlers]]
   (let [base-url (server/http-local-url
                   (system/instance sys [:server :jetty-adapter]))
         config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :meta-store])}]
     (testing "person-party with first-name 'Arthur' goes clear → activates"
       (nom-test> [org-res (create-org base-url "Onfido Test Customer")
                   _ (is (= 201 (:status org-res)))
                   org-body (http/res->edn org-res)
                   token (:api-key-secret org-body)
                   organization-id (:organization-id org-body)
                   party-res (create-party base-url token "Arthur" "Dent")
                   _ (is (= 200 (:status party-res)))
                   party-body (http/res->edn party-res)
                   party-id (:party-id party-body)
                   _ (is (some? party-id))
                   ;; Wait for the chain: party-pending → IDV pending →
                   ;; submit-idv-check → Onfido simulator → check-completed
                   ;; webhook → idv-completed event → IDV accepted → party
                   ;; activated. The simulator's webhook-delay-ms is 500ms,
                   ;; and the watcher / event-processor add their own.
                   activated (poll-party-status config
                                                organization-id
                                                party-id
                                                :party-status-active
                                                15000)
                   _ (is (= :party-status-active (:status activated)))]))
     (testing "person-party with first-name 'Reject' stays pending"
       (nom-test> [org-res (create-org base-url "Onfido Test Reject Customer")
                   _ (is (= 201 (:status org-res)))
                   org-body (http/res->edn org-res)
                   token (:api-key-secret org-body)
                   organization-id (:organization-id org-body)
                   party-res (create-party base-url token "Reject" "Smith")
                   _ (is (= 200 (:status party-res)))
                   party-body (http/res->edn party-res)
                   party-id (:party-id party-body)
                   ;; The chain runs end-to-end but the IDV resolves to
                   ;; rejected; bank-party's watcher only activates on
                   ;; accepted, so the party stays pending. Wait long
                   ;; enough for the chain to complete, then assert.
                   _ (Thread/sleep 5000)
                   final (parties/get-party config organization-id party-id)
                   _ (is (= :party-status-pending (:status final))
                         "consider outcome leaves the party in pending")])))))
