(ns com.repldriven.queenswood.api.jobs.queries
  (:require
    [com.repldriven.queenswood.api.jobs.view :as view]

    [com.repldriven.queenswood.api.errors :as errors]

    [com.repldriven.queenswood.scheduler.interface :as scheduler]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- job-not-found
  [job-id]
  (error/reject :scheduler/job-not-found
                {:message "Scheduled job not found" :job-id job-id}))

(defn list-jobs
  [request]
  (let [{:keys [record-db record-store auth]} request
        {:keys [bank-id]} auth
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [jobs (scheduler/list-jobs config bank-id)]
                 {:jobs (mapv view/job->api jobs)})]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn get-job
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [job-id]} (:path parameters)
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [job (scheduler/get-job config bank-id job-id)
                  _ (when (nil? job) (job-not-found job-id))]
                 (view/job->api job))]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn list-runs
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [job-id]} (:path parameters)
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [job (scheduler/get-job config bank-id job-id)
                  _ (when (nil? job) (job-not-found job-id))
                  runs (scheduler/list-runs config bank-id job-id)]
                 {:runs runs})]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn get-run
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [job-id run-id]} (:path parameters)
        config {:record-db record-db :record-store record-store}
        result (let-nom>
                 [run (scheduler/get-run config bank-id run-id)
                  _ (when (or (nil? run) (not= job-id (:job-id run)))
                      (error/reject :scheduler/run-not-found
                                    {:message "Scheduled run not found"
                                     :job-id job-id
                                     :run-id run-id}))]
                 run)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
