(ns ^:eftest/synchronized com.repldriven.queenswood.cash-account.events-test
  (:require
    [com.repldriven.queenswood.testcontainers.interface]

    [com.repldriven.queenswood.cash-account.changelog :as changelog]
    [com.repldriven.queenswood.cash-account.core :as core]
    [com.repldriven.queenswood.cash-account.store :as store]

    [com.repldriven.queenswood.cash-account-query.interface :as q]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

(def ^:private test-bank-id "bnk_events_test")

(defn- account
  [account-id status]
  {:bank-id test-bank-id
   :account-id account-id
   :party-id "pty.test"
   :product-id "prd.test"
   :version-id "v1"
   :name "Event Redelivery Test Account"
   :currency "GBP"
   :account-status status
   :created-at (utility/now)
   :updated-at (utility/now)})

(deftest redelivered-event-is-a-noop-test
  (with-test-system
   [sys "classpath:cash-account/application-test.yml"]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         account-id "acc.events.1"]
     (testing
       "seed the account already opened, then replay the
              opening->opened transition as if the event had not been
              consumed yet"
       (nom-test> [_ (store/save-account
                      config
                      (account account-id :cash-account-status-opened)
                      {:account-id account-id
                       :status-after :cash-account-status-opening})]))
     (testing
       "the guard skips silently — the loaded account has already
              moved past the expected source status, so replay is a
              no-op rather than a rejection"
       (core/complete-status-transition config
                                        test-bank-id
                                        account-id
                                        :cash-account-status-opening)
       (nom-test> [found (q/find-account config test-bank-id account-id)
                   _ (is (= :cash-account-status-opened
                            (:account-status found)))])))))

(deftest changelog-carries-the-shared-envelope-test
  (testing
    "a status transition serialises as a ChangelogEvent the
           generic relay can decode without knowing this domain"
    (let [bytes (changelog/status-changed
                 {:bank-id test-bank-id
                  :account-id "acc.events.2"
                  :status-before :cash-account-status-opening
                  :status-after :cash-account-status-opened})
          decoded (schema/pb->ChangelogEvent bytes)]
      (is (= "cash-account-status-changed" (:event-name decoded)))
      (is (= "acc.events.2:cash-account-status-opened" (:dedup-key decoded)))
      (is (seq (:event-id decoded)) "an event-id is minted for dedup")
      (is (pos? (count (:payload decoded))) "the Avro payload is carried"))))
