(ns com.repldriven.mono.bank-api.jobs.routes
  (:require
    [com.repldriven.mono.bank-api.jobs.queries :as queries]
    [com.repldriven.mono.bank-api.jobs.examples :refer
     [JobNotFound RunNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

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
     ["/runs"
      [""
       {:get {:summary "Retrieve a job's runs"
              :openapi {:operationId "RetrieveJobRuns"}
              :responses {200 {:body [:ref "RunList"]}
                          404 (ErrorResponse [#'JobNotFound])}
              :handler queries/list-runs}}]
      ["/{run-id}"
       {:parameters {:path {:run-id [:ref "RunId"]}}}
       [""
        {:get {:summary "Retrieve a job run"
               :openapi {:operationId "RetrieveJobRun"}
               :responses {200 {:body [:ref "Run"]}
                           404 (ErrorResponse [#'RunNotFound])}
               :handler queries/get-run}}]]]]]])
