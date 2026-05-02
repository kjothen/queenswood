(ns com.repldriven.mono.bank-idv-onfido-webhook.components
  "Malli schemas for the Onfido-shaped `check.completed` webhook
  callback. Mirrors the publicly-documented Onfido payload shape:

      {:payload {:resource_type \"check\"
                 :action        \"check.completed\"
                 :object        {:id ...
                                 :status \"complete\"
                                 :result \"clear\"|\"consider\"
                                 :completed_at_iso8601 ...}}}

  We only model the surface bank-idv needs to act on — `result`
  is the field that ultimately drives `:idv-status-accepted` vs
  `:idv-status-rejected`."
  (:require
    [com.repldriven.mono.bank-idv-onfido-webhook.examples :as examples]

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
   ;; Simulator extension — Onfido proper doesn't carry an external
   ;; correlation field on `check.completed`. We add one so the
   ;; adapter can correlate the webhook back to its originating
   ;; `:verification-id` without holding state. Production callers
   ;; that ever swap this simulator out for the real Onfido SaaS
   ;; would need a real correlation strategy (Onfido `tags`, or a
   ;; persistent `{check-id -> verification-id}` lookup).
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
