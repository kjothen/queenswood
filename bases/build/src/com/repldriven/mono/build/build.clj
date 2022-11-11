(ns com.repldriven.mono.build.build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(defn uber
  "Build a polylith project uberjar"
  [opts]
  (let [major-minor-version (get opts :major-minor-version "0.0")
        patch-version
        (if (:snapshot opts) "999-SNAPSHOT" (b/git-count-revs nil))
        version (format "%s.%s" major-minor-version patch-version)]
    (-> opts
        (assoc :version version
               :transitive true
               :conflict-handlers
               {"^data_readers.clj[cs]?$" :data-readers
                "^META-INF/services/.*" :append
                "(?i)^(META-INF/)?(COPYRIGHT|NOTICE|LICENSE)(\\.(txt|md))?$"
                :ignore
                :default :ignore})
        (bb/clean)
        (bb/uber))))

(defn avro
  [{:keys [avro-tools-version src target]
    :or {avro-tools-version "1.11.0" src "resources" target "generated"}}]
  (let [home (System/getenv "HOME")
        command-args
        ["java" "-jar"
         (format
          "%s/.m2/repository/org/apache/avro/avro-tools/%s/avro-tools-%s.jar"
          home
          avro-tools-version
          avro-tools-version) "compile" "schema" src target]]
    (b/delete {:path target})
    (b/process {:command-args command-args})))
