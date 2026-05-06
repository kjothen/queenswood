(ns com.repldriven.mono.bank-test-scenarios.scenario
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
  [scenario]
  (let [{:keys [given then]} scenario
        when-steps (:when scenario)]
    (vec (concat given when-steps then))))

(defn from-resource
  [resource-path]
  (error/let-nom>
    [src (or (io/resource resource-path)
             (error/fail :bank-test-scenarios/scenario
                         {:message "Scenario resource not found"
                          :resource resource-path}))
     parsed (error/try-nom :bank-test-scenarios/scenario
                           "Failed to parse scenario EDN"
                           (edn/read-string (slurp src)))
     _ (when-not (spec/validate schema parsed)
         (error/fail :bank-test-scenarios/scenario
                     {:message "Scenario failed schema validation"
                      :resource resource-path
                      :explain (spec/humanize
                                (spec/explain schema parsed))}))]
    parsed))
