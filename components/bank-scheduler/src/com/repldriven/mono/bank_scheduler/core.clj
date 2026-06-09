(ns com.repldriven.mono.bank-scheduler.core
  (:require
    [com.repldriven.mono.bank-scheduler.domain :as domain]
    [com.repldriven.mono.bank-scheduler.store :as store]

    [com.repldriven.mono.bank-interest.interface :as interest]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.scheduler.interface :as scheduler]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.edn :as edn]
    [clojure.java.io :as io]))

;; Code-defined registry of preset tasks. Each `:run` takes the FDB+
;; interfaces config, the bank id, and an as-of date (epoch-day), and
;; returns a result map or an anomaly. account-migration has no run yet
;; (net-new domain op) — a job carrying it fails at run with an
;; unknown-task rejection until one is registered.
(def ^:private task-registry
  {:scheduler-task-kind-accrue
   {:label "accrue"
    :run (fn [config bank-id as-of-date]
           (interest/accrue-daily config
                                  {:bank-id bank-id :as-of-date as-of-date}))}
   :scheduler-task-kind-capitalize {:label "capitalize"
                                    :run (fn [config bank-id as-of-date]
                                           (interest/capitalize-monthly
                                            config
                                            {:bank-id bank-id
                                             :as-of-date as-of-date}))}})

(defn- task-label
  [task-kind]
  (get-in task-registry [task-kind :label] (name task-kind)))

(defn- trigger-id
  "Scheduler trigger name for a job. cronut keys jobs within one shared
  group, so the bank id is folded in to keep names unique across banks."
  [job]
  (str (:bank-id job) "/" (:job-id job)))

(defn- job-cron
  [job]
  (domain/->cron (:periodicity job) (:run-time-minutes job)))

(def ^:private default-jobs
  "Default scheduled jobs seeded into every bank at provisioning,
  loaded once from the bank-resources classpath. Templates carry no
  bank id / timestamps — those are stamped at seed time."
  (let [path "bank/scheduler/jobs.edn"
        url (io/resource path)]
    (when (nil? url)
      (throw (ex-info "Default scheduler jobs resource missing" {:path path})))
    (edn/read-string (slurp url))))

;; --- queries --------------------------------------------------------------

(defn list-jobs
  [config bank-id]
  (store/list-jobs config bank-id))

(defn get-job
  [config bank-id job-id]
  (store/get-job config bank-id job-id))

(defn get-run
  [config bank-id run-id]
  (store/get-run config bank-id run-id))

(defn list-runs
  [config bank-id job-id]
  (store/list-runs-by-job config bank-id job-id))

;; --- run engine -----------------------------------------------------------

(defn- last-succeeded-run
  [config bank-id job-id]
  (let [runs (store/list-runs-by-job config bank-id job-id)]
    (when-not (error/anomaly? runs)
      (first (filter #(= :scheduler-run-status-succeeded (:status %)) runs)))))

(defn run-job
  "Execute `job`'s tasks sequentially, recording a `SchedulerRun` with
  task-granular progress. Opens the run as running, advances
  `tasks-completed` / `current-task` after each task, and finishes
  succeeded or — on the first task anomaly — failed (remaining tasks are
  skipped). `as-of-date` is today (epoch-day); the underlying interest
  tasks are idempotent and guarded by the daily-limit policy, so a
  re-run records a failed run rather than double-posting. Returns the
  final run map, or the task anomaly on failure."
  [config bank-id job trigger-source]
  (let [run-id (str (utility/uuidv7))
        started-at (utility/now)
        as-of-date (utility/today)
        task-kinds (vec (:task-kinds job))
        prev (last-succeeded-run config bank-id (:job-id job))
        base (utility/assoc-some
              {:bank-id bank-id
               :run-id run-id
               :job-id (:job-id job)
               :trigger-source trigger-source
               :started-at started-at
               :tasks-total (count task-kinds)}
              :expected-end-at
              (domain/expected-end-at started-at prev))]
    (store/save-run config
                    (assoc base
                           :status :scheduler-run-status-running
                           :tasks-completed 0
                           :current-task (task-label (first task-kinds))))
    (loop [[task-kind & more] task-kinds
           completed 0]
      (if (nil? task-kind)
        (let [run (assoc base
                         :status :scheduler-run-status-succeeded
                         :tasks-completed completed
                         :finished-at (utility/now))]
          (store/save-run config run)
          (store/save-job config
                          (assoc job
                                 :last-run-at started-at
                                 :next-run-at (scheduler/next-fire-at
                                               (job-cron job)
                                               started-at)
                                 :updated-at (utility/now)))
          run)
        (let [run-fn (get-in task-registry [task-kind :run])
              result (if run-fn
                       (run-fn config bank-id as-of-date)
                       (error/reject :scheduler/unknown-task
                                     {:message "No run registered for task"
                                      :task-kind task-kind}))]
          (if (error/anomaly? result)
            (let [run (assoc base
                             :status :scheduler-run-status-failed
                             :tasks-completed completed
                             :current-task (task-label task-kind)
                             :finished-at (utility/now)
                             :error (error/format-anomaly result))]
              (store/save-run config run)
              result)
            (do
              (store/save-run config
                              (assoc base
                                     :status :scheduler-run-status-running
                                     :tasks-completed (inc completed)
                                     :current-task (task-label task-kind)))
              (recur more (inc completed)))))))))

(defn force-start
  "Run `job-id` now with trigger source forced. Safe to repeat — the
  tasks are idempotent and the daily limit guards double-accrual."
  [config bank-id job-id]
  (let [job (store/get-job config bank-id job-id)]
    (cond
     (error/anomaly? job)
     job

     (nil? job)
     (error/reject :scheduler/job-not-found
                   {:bank-id bank-id :job-id job-id})

     :else
     (run-job config bank-id job :scheduler-trigger-source-forced))))

;; --- editing --------------------------------------------------------------

(defn update-schedule
  "Edit a job's periodicity / run-time / enabled flag, within the
  periodicities its tasks allow. Persists the change, recomputes
  `next-run-at`, and reflects it on the live trigger when a scheduler is
  present in `config`."
  [config bank-id job-id {:keys [periodicity run-time-minutes enabled]}]
  (let [job (store/get-job config bank-id job-id)]
    (cond
     (error/anomaly? job)
     job

     (nil? job)
     (error/reject :scheduler/job-not-found
                   {:bank-id bank-id :job-id job-id})

     :else
     (let [periodicity (or periodicity (:periodicity job))
           run-time-minutes (or run-time-minutes (:run-time-minutes job))
           enabled (if (some? enabled) enabled (:enabled job))]
       (let-nom> [_ (domain/validate-periodicity (:task-kinds job) periodicity)]
         (let [now (utility/now)
               cron (domain/->cron periodicity run-time-minutes)
               updated (assoc job
                              :periodicity periodicity
                              :run-time-minutes run-time-minutes
                              :enabled enabled
                              :next-run-at (scheduler/next-fire-at cron now)
                              :updated-at now)
               result (store/save-job config updated)]
           (if (error/anomaly? result)
             result
             (do
               (when-let [sched (:scheduler config)]
                 (if enabled
                   (scheduler/schedule!
                    sched
                    (trigger-id updated)
                    cron
                    #(run-job config
                              bank-id
                              updated
                              :scheduler-trigger-source-scheduled))
                   (scheduler/unschedule! sched (trigger-id updated))))
               updated))))))))

;; --- seeding + trigger registration --------------------------------------

(defn seed-jobs
  "Seed the bank's default scheduled jobs (FDB only — no triggers).
  Idempotent: re-seeding overwrites by `[bank_id, job_id]`. Runs inside
  the caller's transaction so a failed row rolls bank creation back."
  [txn bank-id]
  (reduce (fn [_ template]
            (let [now (utility/now)
                  cron (domain/->cron (:periodicity template)
                                      (:run-time-minutes template))
                  job (assoc template
                             :bank-id bank-id
                             :next-run-at (scheduler/next-fire-at cron now)
                             :created-at now
                             :updated-at now)
                  result (store/save-job txn job)]
              (if (error/anomaly? result) (reduced result) nil)))
          nil
          default-jobs))

(defn register-all!
  "Register a cronut trigger for every enabled job across all banks.
  Called by the runner at startup. `config` must carry `:scheduler`."
  [config]
  (let [sched (:scheduler config)
        jobs (store/list-all-jobs config)]
    (if (error/anomaly? jobs)
      jobs
      (do
        (doseq [job (filter :enabled jobs)]
          (scheduler/schedule!
           sched
           (trigger-id job)
           (job-cron job)
           (fn []
             (run-job config
                      (:bank-id job)
                      job
                      :scheduler-trigger-source-scheduled))))
        nil))))
