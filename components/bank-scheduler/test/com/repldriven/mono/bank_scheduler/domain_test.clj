(ns com.repldriven.mono.bank-scheduler.domain-test
  "Pure-function tests for the scheduler domain: per-task periodicity
  constraints, the job-allowed intersection, periodicity→cron, and the
  expected-end estimate. No FDB, no scheduler."
  (:require
    [com.repldriven.mono.bank-scheduler.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest job-allowed-periods-test
  (testing "accrue is daily-only"
    (is (= #{:scheduler-periodicity-daily}
           (SUT/job-allowed-periods [:scheduler-task-kind-accrue]))))
  (testing "capitalize allows daily/monthly/yearly"
    (is (= SUT/all-periods
           (SUT/job-allowed-periods [:scheduler-task-kind-capitalize]))))
  (testing "a sequence is the intersection — accrue narrows the job to daily"
    (is (= #{:scheduler-periodicity-daily}
           (SUT/job-allowed-periods [:scheduler-task-kind-accrue
                                     :scheduler-task-kind-capitalize]))))
  (testing "no tasks allows nothing" (is (= #{} (SUT/job-allowed-periods [])))))

(deftest validate-periodicity-test
  (testing "allowed periodicity passes (nil)"
    (is (nil? (SUT/validate-periodicity [:scheduler-task-kind-capitalize]
                                        :scheduler-periodicity-monthly))))
  (testing "disallowed periodicity rejects"
    (let [result (SUT/validate-periodicity [:scheduler-task-kind-accrue
                                            :scheduler-task-kind-capitalize]
                                           :scheduler-periodicity-monthly)]
      (is (error/rejection? result))
      (is (= :scheduler/periodicity-not-allowed (error/kind result))))))

(deftest ->cron-test
  (testing "run-time-minutes splits into hour/minute; 120 = 02:00"
    (is (= "0 0 2 * * ?" (SUT/->cron :scheduler-periodicity-daily 120))))
  (testing "monthly fires on the 1st"
    (is (= "0 30 6 1 * ?" (SUT/->cron :scheduler-periodicity-monthly 390))))
  (testing "yearly fires on Jan 1"
    (is (= "0 0 0 1 1 ?" (SUT/->cron :scheduler-periodicity-yearly 0)))))

(deftest expected-end-at-test
  (testing "started-at plus the prior run's duration"
    (is (= 1150 (SUT/expected-end-at 1000 {:started-at 100 :finished-at 250}))))
  (testing "nil when the prior run never finished"
    (is (nil? (SUT/expected-end-at 1000 {:started-at 100})))
    (is (nil? (SUT/expected-end-at 1000 nil)))))
