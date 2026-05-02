(ns com.repldriven.mono.bank-idv-onfido-simulator.schema
  (:require
    [com.repldriven.mono.utility.interface :refer [vname]]))

(defn components-registry
  [vars]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} vars))

(defn examples-registry
  [examples]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} examples))

(def ErrorResponseSchema
  [:map
   [:title string?]
   [:type string?]
   [:status int?]
   [:detail {:optional true} string?]])
