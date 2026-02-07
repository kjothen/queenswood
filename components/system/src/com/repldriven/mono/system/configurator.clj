(ns com.repldriven.mono.system.configurator
  (:require [com.repldriven.mono.utility.interface :as utility]))

;; System Configuration Structure
;;
;; The system configuration follows a nested structure of component groups and components:
;;
;; {:component-group-1
;;  {:component-a {:kind :namespace/component-a
;;                 :config {...}}
;;   :component-b {:kind :namespace/component-b
;;                 :config {...}}}
;;  :component-group-2
;;  {:component-c {:kind :namespace/component-c
;;                 :config {...}}}}
;;
;; For example, a server configuration might look like:
;;
;; {:server
;;  {:interceptors {:kind :server/interceptors
;;                  :datasource ...}
;;   :jetty-adapter {:kind :server/jetty-adapter
;;                   :handler ... :options {:port 8080}}}}
;;
;; The `definition` function processes this structure by:
;; 1. Reducing over component groups (:server, :sql, etc.)
;; 2. Reducing over components within each group
;; 3. Calling the `component` multimethod for each component
;;    (which dispatches on :kind)
;; 4. Building [:system/defs {...}] for donut.system

(defn merge-component-config
  [component config]
  (update component
          :system/config
          (fn [original] (utility/deep-merge original (dissoc config :kind)))))

(defmulti component
  "Component configuration multimethod.
  Dispatches on the component kind.
  Components should extend this to register themselves."
  (fn [_ v] (keyword (:kind v))))

(defmethod component :default
  [_ v]
  v)

(defmacro defcomponents
  "Defines multiple system/component defmethods for a given namespace.

  Usage:
    (defcomponents :server
      {:interceptors components/interceptors
       :jetty-adapter components/jetty-adapter})

  Expands to:
    (defmethod component :server/interceptors [_ v]
      (merge-component-config components/interceptors v))
    (defmethod component :server/jetty-adapter [_ v]
      (merge-component-config components/jetty-adapter v))"
  [ns-keyword component-map]
  `(do
     ~@(for [[component-name component-def] component-map]
         `(defmethod component ~(keyword (name ns-keyword)
                                          (name component-name))
            [~'_ ~'v]
            (merge-component-config ~component-def ~'v)))))

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
