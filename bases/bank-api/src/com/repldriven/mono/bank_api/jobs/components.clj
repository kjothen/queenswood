(ns com.repldriven.mono.bank-api.jobs.components
  (:require
    [com.repldriven.mono.bank-api.jobs.coercion :as coercion]
    [com.repldriven.mono.bank-api.jobs.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def JobId
  [:re
   {:title "JobId"
    :json-schema/example examples/JobId
    :description "Stable per-bank job slug, e.g. \"daily-interest\"."}
   #"^[a-z0-9]+(-[a-z0-9]+)*$"])

(def RunId
  [:re
   {:title "RunId"
    :json-schema/example examples/RunId
    :description "Time-ordered run identifier (uuidv7)."}
   #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"])

(def Periodicity
  (coercion/periodicity-enum-schema {:json-schema/example "daily"}))

(def JobTaskKind
  (coercion/task-kind-enum-schema {:json-schema/example "accrue"}))

(def RunStatus
  (coercion/run-status-enum-schema {:json-schema/example "running"}))

(def TriggerSource
  (coercion/trigger-source-enum-schema {:json-schema/example "scheduled"}))

(def Job
  [:map {:json-schema/example examples/Job}
   [:bank-id [:ref "BankId"]]
   [:job-id [:ref "JobId"]]
   [:name [:ref "Name"]]
   [:task-kinds [:vector [:ref "JobTaskKind"]]]
   [:periodicity [:ref "Periodicity"]]
   ;; Minutes past midnight (UTC) the job fires on each scheduled day.
   [:run-time-minutes [:int {:min 0 :max 1439}]]
   [:enabled boolean?]
   [:last-run-at {:optional true} [:ref "Timestamp"]]
   [:next-run-at {:optional true} [:ref "Timestamp"]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def JobList
  [:map {:json-schema/example examples/JobList}
   [:jobs [:vector [:ref "Job"]]]])

(def Run
  [:map {:json-schema/example examples/Run}
   [:bank-id [:ref "BankId"]]
   [:run-id [:ref "RunId"]]
   [:job-id [:ref "JobId"]]
   [:status [:ref "RunStatus"]]
   [:trigger-source [:ref "TriggerSource"]]
   [:started-at [:ref "Timestamp"]]
   [:finished-at {:optional true} [:ref "Timestamp"]]
   [:tasks-total nat-int?]
   [:tasks-completed nat-int?]
   [:current-task {:optional true} string?]
   [:expected-end-at {:optional true} [:ref "Timestamp"]]
   [:error {:optional true} string?]])

(def RunList
  [:map {:json-schema/example examples/RunList}
   [:runs [:vector [:ref "Run"]]]])

(def registry
  (components-registry [#'JobId #'RunId #'Periodicity #'JobTaskKind #'RunStatus
                        #'TriggerSource #'Job #'JobList #'Run #'RunList]))
