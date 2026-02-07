(ns com.repldriven.mono.env.reader.yml
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [flatland.ordered.map]
            [com.repldriven.mono.env.reader.edn :as reader.edn]
            [com.repldriven.mono.utility.interface :as util]))

(defmulti yml-reader (fn [m] (keyword (get m :tag))))

(defmulti tag-reader (fn [m] (keyword (get m :tag))))

;; Basic common tags
(defmethod tag-reader :!profile
  [{:keys [value]}]
  (symbol (str "#profile " (util/yaml-collections->edn-collections value))))

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
        util/yaml-collections->edn-collections)))

(defmethod tag-reader :!strs [{:keys [value]}] (util/keys->strs value))

(defmethod tag-reader :!str [{:keys [value]}] (str "\"" (name value) "\""))

(defmethod tag-reader :!keyword [{:keys [value]}] (keyword value))

(defmethod tag-reader :default [m] (:value m))

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
      util/yaml-collections->edn-collections
      str
      util/string->stream
      (reader.edn/read-config profile)))
