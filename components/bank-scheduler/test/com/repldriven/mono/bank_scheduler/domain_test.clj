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
  (testing "monthly defaults to the 1st"
    (is (= "0 30 6 1 * ?" (SUT/->cron :scheduler-periodicity-monthly 390))))
  (testing "monthly first day is explicit day 1"
    (is (= "0 30 6 1 * ?"
           (SUT/->cron :scheduler-periodicity-monthly
                       390
                       :scheduler-monthly-day-first))))
  (testing "monthly last day uses the Quartz L token"
    (is (= "0 30 6 L * ?"
           (SUT/->cron :scheduler-periodicity-monthly
                       390
                       :scheduler-monthly-day-last))))
  (testing "an unknown monthly-day falls back to the 1st"
    (is (= "0 30 6 1 * ?"
           (SUT/->cron :scheduler-periodicity-monthly
                       390
                       :scheduler-monthly-day-unknown))))
  (testing "yearly fires on Jan 1"
    (is (= "0 0 0 1 1 ?" (SUT/->cron :scheduler-periodicity-yearly 0)))))

(deftest system?-test
  (testing "system kind is system"
    (is (SUT/system? {:kind :scheduler-job-kind-system})))
  (testing "user / unknown / absent kinds are not system"
    (is (not (SUT/system? {:kind :scheduler-job-kind-user})))
    (is (not (SUT/system? {:kind :scheduler-job-kind-unknown})))
    (is (not (SUT/system? {})))))

(deftest validate-system-edits-test
  (let [system {:job-id "account-migration" :kind :scheduler-job-kind-system}
        user {:job-id "daily-interest" :kind :scheduler-job-kind-user}]
    (testing "editing only the time of a system job is allowed (nil)"
      (is (nil? (SUT/validate-system-edits system {:run-time-minutes 300}))))
    (testing "a system job rejects cadence / enabled edits"
      (doseq [edit [{:periodicity :scheduler-periodicity-daily}
                    {:monthly-day :scheduler-monthly-day-first}
                    {:enabled false}]]
        (let [result (SUT/validate-system-edits system edit)]
          (is (error/rejection? result))
          (is (= :scheduler/system-job-locked (error/kind result))))))
    (testing "a user job allows any edit (nil)"
      (is (nil? (SUT/validate-system-edits user
                                           {:periodicity
                                            :scheduler-periodicity-monthly
                                            :enabled false}))))))

(deftest expected-end-at-test
  (testing "started-at plus the prior run's duration"
    (is (= 1150 (SUT/expected-end-at 1000 {:started-at 100 :finished-at 250}))))
  (testing "nil when the prior run never finished"
    (is (nil? (SUT/expected-end-at 1000 {:started-at 100})))
    (is (nil? (SUT/expected-end-at 1000 nil)))))
