(ns com.repldriven.mono.env.config.yml
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :refer [postwalk]]
            [clj-yaml.core :as yaml]
            [flatland.ordered.map]
            [com.repldriven.mono.env.config.edn :as config.edn]))

(defmulti yml-reader (fn [m] (keyword (get m :tag))))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(defn yaml-collections->edn-collections
  [form]
  (postwalk #(cond (= "class flatland.ordered.map.OrderedMap" (str (type %)))
                   (into (hash-map) %)
                   (seq? %) (into (vector) %)
                   :else %)
            form))

(defn keys->strs
  [form]
  (postwalk #(if (map? %) (into (hash-map) (map (fn [[k v]] [(name k) v]) %)) %)
            form))

(defmulti tag-reader (fn [m] (keyword (get m :tag))))

(defmethod tag-reader :!profile
  [{:keys [value]}]
  (symbol (str "#profile " (yaml-collections->edn-collections value))))

(defmethod tag-reader :!port [{:keys [value]}] (symbol (str "#port " value)))

(defmethod tag-reader :!include
  [{:keys [value]}]
  (let [key-fn (fn [{:keys [key]}]
                 (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
                   (subs key 1 (dec (count key)))
                   (keyword key)))]
    (-> value
        io/resource
        io/reader
        (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn tag-reader})
        yaml-collections->edn-collections)))

(defmethod tag-reader :!system/required-component
  [m]
  (yml-reader m))

(defmethod tag-reader :!system/ref
  [m]
  (yml-reader m))

(defmethod tag-reader :!system/local-ref
  [m]
  (yml-reader m))

(defmethod tag-reader :!pubsub/crypto-failure-action [m] (yml-reader m))
(defmethod tag-reader :!pubsub/message-id [m] (yml-reader m))
(defmethod tag-reader :!pubsub/schema [m] (yml-reader m))
(defmethod tag-reader :!pubsub/subscription-type [m] (yml-reader m))

(defmethod tag-reader :!system/component
  [{:keys [value]}]
  (let [value-map (yaml-collections->edn-collections value)
        component-kind (get value-map :system/component-kind)
        config (dissoc value-map :system/component-kind)
        config-edn (into {} (map (fn [[k v]] [(keyword k) v]) config))]
    (assoc config-edn :system/component-kind (keyword component-kind))))

(defmethod tag-reader :!strs [{:keys [value]}] (keys->strs value))

(defmethod tag-reader :!str [{:keys [value]}] (str "\"" (name value) "\""))

(defmethod tag-reader :!keyword [{:keys [value]}] (keyword value))

(defmethod tag-reader :default [m] (:value m))

;; Default implementations (extended by system component)
(defmethod yml-reader :!system/required-component
  [_]
  (symbol "#system required-component"))

(defmethod yml-reader :!system/ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system ref " (pr-str (mapv keyword ks)) "]"))))

(defmethod yml-reader :!system/local-ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system local-ref " (pr-str (mapv keyword ks)) "]"))))

(defn key-fn
  [{:keys [key]}]
  (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
    (subs key 1 (dec (count key)))
    (keyword key)))

(defn- resolve-source
  "Resolve a source string to a resource. Handles classpath: prefix."
  [source]
  (if (str/starts-with? source "classpath:")
    (io/resource (subs source (count "classpath:")))
    source))

(defn config
  [source profile]
  (-> (resolve-source source)
      io/reader
      (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn tag-reader})
      yaml-collections->edn-collections
      str
      string->stream
      (config.edn/read-config profile)))
