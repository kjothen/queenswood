(ns com.repldriven.mono.system.reader.yml
  (:refer-clojure :exclude [ref])
  (:require [clojure.string :as str]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.utility.interface :as util]))

(defn local-ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system local-ref " (pr-str (mapv keyword ks)) "]"))))

(defn ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system ref " (pr-str (mapv keyword ks)) "]"))))

(defn required-component [_] (symbol (str "#system required-component")))

;; System tag-reader defmethods (for YAML parsing)
(defmethod env/tag-reader :!system/required-component
  [m]
  (env/yml-reader m))

(defmethod env/tag-reader :!system/ref
  [m]
  (env/yml-reader m))

(defmethod env/tag-reader :!system/local-ref
  [m]
  (env/yml-reader m))

(defmethod env/tag-reader :!system/component
  [{:keys [value]}]
  (let [value-map (util/yaml-collections->edn-collections value)
        component-kind (get value-map :system/component-kind)
        config (dissoc value-map :system/component-kind)
        config-edn (into {} (map (fn [[k v]] [(keyword k) v]) config))]
    (assoc config-edn :system/component-kind (keyword component-kind))))

;; System yml-reader defmethods (for converting to EDN)
(defmethod env/yml-reader :!system/required-component
  [m]
  (required-component m))

(defmethod env/yml-reader :!system/ref
  [m]
  (ref m))

(defmethod env/yml-reader :!system/local-ref
  [m]
  (local-ref m))
