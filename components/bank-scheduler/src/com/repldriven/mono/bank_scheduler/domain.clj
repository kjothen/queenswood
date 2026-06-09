(ns com.repldriven.mono.bank-scheduler.domain
  (:require
    [clojure.set :as set]
    [com.repldriven.mono.error.interface :as error]))

(def all-periods
  #{:scheduler-periodicity-daily :scheduler-periodicity-monthly
    :scheduler-periodicity-yearly})

;; Per-task periodicity constraints. Accrual must run once per day;
;; capitalization and account-migration may run on any cadence.
(def task-allowed-periods
  {:scheduler-task-kind-accrue #{:scheduler-periodicity-daily}
   :scheduler-task-kind-capitalize all-periods
   :scheduler-task-kind-account-migration all-periods})

(defn job-allowed-periods
  "Periodicities a job may use — the intersection of its tasks'
  allowed periodicities. A job with no tasks allows none."
  [task-kinds]
  (if (seq task-kinds)
    (reduce (fn [acc kind]
              (set/intersection acc (task-allowed-periods kind all-periods)))
            all-periods
            task-kinds)
    #{}))

(defn periodicity-allowed?
  [task-kinds periodicity]
  (contains? (job-allowed-periods task-kinds) periodicity))

(defn validate-periodicity
  "Rejects when `periodicity` is not in the job's allowed set."
  [task-kinds periodicity]
  (when-not (periodicity-allowed? task-kinds periodicity)
    (error/reject :scheduler/periodicity-not-allowed
                  {:message "Periodicity not allowed for this job's tasks"
                   :periodicity periodicity
                   :allowed (job-allowed-periods task-kinds)})))

(defn ->cron
  "Quartz 6-field cron expression for a periodicity firing at
  `run-time-minutes` past midnight (UTC). Daily fires every day;
  monthly on the 1st; yearly on Jan 1. Seconds are always 0."
  [periodicity run-time-minutes]
  (let [h (quot run-time-minutes 60)
        m (mod run-time-minutes 60)]
    (case periodicity
      :scheduler-periodicity-daily (format "0 %d %d * * ?" m h)
      :scheduler-periodicity-monthly (format "0 %d %d 1 * ?" m h)
      :scheduler-periodicity-yearly (format "0 %d %d 1 1 ?" m h))))

(defn run-duration
  "Wall-clock duration of a completed run, or nil if it lacks a
  finish."
  [run]
  (when (and (:started-at run) (:finished-at run))
    (- (:finished-at run) (:started-at run))))

(defn expected-end-at
  "`started-at` plus the previous successful run's duration, or nil
  when there is no completed prior run to estimate from."
  [started-at prev-run]
  (when-let [duration (run-duration prev-run)]
    (+ started-at duration)))
