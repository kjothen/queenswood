(ns com.repldriven.mono.system.configurator
  (:require [com.repldriven.mono.utility.interface :as utility]))

;; System Configuration Structure
;;
;; The system configuration follows a nested structure of component groups and components:
;;
;; {:component-group-1
;;  {:component-a {:annotation {:component/definition :namespace/component-a}
;;                 :config {...}}
;;   :component-b {:annotation {:component/definition :namespace/component-b}
;;                 :config {...}}}
;;  :component-group-2
;;  {:component-c {:annotation {:component/definition :namespace/component-c}
;;                 :config {...}}}}
;;
;; For example, a server configuration might look like:
;;
;; {:server
;;  {:interceptors {:annotation {:component/definition :server/interceptors}
;;                  :config {:datasource ...}}
;;   :jetty-adapter {:annotation {:component/definition :server/jetty-adapter}
;;                   :config {:handler ... :options {:port 8080}}}}}
;;
;; The `definition` function processes this structure by:
;; 1. Reducing over component groups (:server, :sql, etc.)
;; 2. Reducing over components within each group
;; 3. Calling the `component` multimethod for each component
;;    (which dispatches on [:annotation :component/definition])
;; 4. Building [:system/defs {...}] for donut.system

(defn merge-component-config
  [component config]
  (update component
          :system/config
          (fn [original] (utility/deep-merge original config))))

(defmulti component
  "Component configuration multimethod.
  Dispatches on the component definition from annotation metadata.
  Components should extend this to register themselves."
  (fn [_ v] (keyword (get-in v [:annotation :component/definition]))))

(defmethod component :default
  [_ v]
  v)

(defn- component-group
  "Processes a component group by reducing over its components."
  [group-config]
  (reduce-kv (fn [components component-name component-config]
               (assoc components component-name
                      (component component-name component-config)))
             {}
             group-config))

(defn definition
  "Builds system definitions by reducing over component groups and their components."
  [config]
  {:system/defs
   (reduce-kv (fn [groups group-name group-config]
                (assoc groups group-name (component-group group-config)))
              {}
              config)})
