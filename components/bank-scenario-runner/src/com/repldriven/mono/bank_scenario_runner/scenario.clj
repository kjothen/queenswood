(ns com.repldriven.mono.bank-scenario-runner.scenario
  "Hand-authored EDN scenario format. A scenario is a single map
  whose `:given`/`:when`/`:then` sections are sequences of the
  same step shape that fugato produces — `{:command kw :args [...]}`
  — so the runner can dispatch them with one mechanism. The
  given/when/then split is for human readers; the runner just
  concatenates them.

  Schema-validated at load time so typos in scenarios fail loudly
  with a useful error rather than producing mysterious dispatch
  failures."
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.spec.interface :as spec]

    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def step
  [:map
   [:command keyword?]
   [:args [:vector any?]]])

(def schema
  [:map
   [:name string?]
   [:tags {:optional true} [:set keyword?]]
   [:given {:optional true} [:vector step]]
   [:when [:vector step]]
   [:then {:optional true} [:vector step]]])

(defn steps
  "Concatenated `:given`/`:when`/`:then` steps in order, ready for
  the runner."
  [scenario]
  (let [{:keys [given then]} scenario
        when-steps (:when scenario)]
    (vec (concat given when-steps then))))

(defn from-resource
  "Read and validate the EDN scenario at `resource-path` (a
  classpath resource). Returns the parsed scenario map, or an
  anomaly if the file is missing or fails schema validation."
  [resource-path]
  (error/let-nom>
    [src (or (io/resource resource-path)
             (error/fail :bank-scenario-runner/scenario
                         {:message "Scenario resource not found"
                          :resource resource-path}))
     parsed (error/try-nom :bank-scenario-runner/scenario
                           "Failed to parse scenario EDN"
                           (edn/read-string (slurp src)))
     _ (when-not (spec/validate schema parsed)
         (error/fail :bank-scenario-runner/scenario
                     {:message "Scenario failed schema validation"
                      :resource resource-path
                      :explain (spec/humanize
                                (spec/explain schema parsed))}))]
    parsed))
