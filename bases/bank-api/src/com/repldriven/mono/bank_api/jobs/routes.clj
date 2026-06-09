(ns com.repldriven.mono.bank-api.jobs.routes
  (:require
    [com.repldriven.mono.bank-api.jobs.handlers :as handlers]
    [com.repldriven.mono.bank-api.jobs.queries :as queries]
    [com.repldriven.mono.bank-api.jobs.examples :refer
     [JobNotFound RunNotFound PeriodicityNotAllowed]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def ^:private run-location-header
  {:schema {:type "string"} :description "URI of the newly-started run"})

(def routes
  [["/jobs"
    {:openapi {:tags ["Jobs"] :security [{"bearerAuth" ["org"]}]}}
    [""
     {:get {:summary "Retrieve scheduled jobs"
            :openapi {:operationId "RetrieveJobs"}
            :responses {200 {:body [:ref "JobList"]}}
            :handler queries/list-jobs}}]
    ["/{job-id}"
     {:parameters {:path {:job-id [:ref "JobId"]}}}
     [""
      {:get {:summary "Retrieve a scheduled job"
             :openapi {:operationId "RetrieveJob"}
             :responses {200 {:body [:ref "Job"]}
                         404 (ErrorResponse [#'JobNotFound])}
             :handler queries/get-job}}]
     ["/schedule"
      {:put {:summary "Update a job's schedule (cadence, time, enabled)"
             :openapi {:operationId "UpdateJobSchedule"
                       :requestBody {:required true}}
             :parameters {:body [:ref "JobScheduleUpdate"]}
             :responses {200 {:body [:ref "Job"]}
                         404 (ErrorResponse [#'JobNotFound])
                         422 (ErrorResponse [#'PeriodicityNotAllowed])}
             :handler handlers/update-schedule}}]
     ["/runs"
      [""
       {:get {:summary "Retrieve a job's runs"
              :openapi {:operationId "RetrieveJobRuns"}
              :responses {200 {:body [:ref "RunList"]}
                          404 (ErrorResponse [#'JobNotFound])}
              :handler queries/list-runs}
        :post {:summary "Force-start the job now"
               :openapi {:operationId "StartJobRun"}
               :responses {201 {:body [:ref "Run"]
                                :openapi {:headers {"Location"
                                                    run-location-header}}}
                           404 (ErrorResponse [#'JobNotFound])}
               :handler handlers/start-run}}]
      ["/{run-id}"
       {:parameters {:path {:run-id [:ref "RunId"]}}}
       [""
        {:get {:summary "Retrieve a job run"
               :openapi {:operationId "RetrieveJobRun"}
               :responses {200 {:body [:ref "Run"]}
                           404 (ErrorResponse [#'RunNotFound])}
               :handler queries/get-run}}]]]]]])
