(ns com.repldriven.queenswood.onfido-webhook.components
  (:require
    [com.repldriven.queenswood.onfido-webhook.examples :as examples]

    [com.repldriven.mono.utility.interface :refer [vname]]))

(defn- components-registry
  [vars]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} vars))

(defn- examples-registry
  [vars]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} vars))

(def CheckCompletedObject
  [:map
   {:json-schema/example examples/CheckCompletedObject}
   [:id string?]
   [:status [:= "complete"]]
   [:result [:enum "clear" "consider"]]
   [:completed_at_iso8601 {:optional true} [:maybe string?]]
   ;; Simulator-only field. Real Onfido `check.completed` carries no
   ;; external correlation key; production would correlate via
   ;; Onfido `tags` or a persistent check-id lookup.
   [:external_id {:optional true} [:maybe string?]]])

(def CheckCompletedPayload
  [:map
   {:json-schema/example examples/CheckCompletedPayload}
   [:resource_type [:= "check"]]
   [:action [:= "check.completed"]]
   [:object [:ref "CheckCompletedObject"]]])

(def CheckCompletedWebhook
  [:map
   {:json-schema/example examples/CheckCompletedWebhook}
   [:payload [:ref "CheckCompletedPayload"]]])

(def component-registry
  (components-registry [#'CheckCompletedObject #'CheckCompletedPayload
                        #'CheckCompletedWebhook]))

(def example-registry
  (examples-registry [#'examples/CheckCompletedObject
                      #'examples/CheckCompletedPayload
                      #'examples/CheckCompletedWebhook]))
