(ns com.repldriven.mono.build.build
  (:require
    [org.corfield.build :as bb]

    [clojure.tools.build.api :as b]
    [clojure.java.io :as io]
    [clojure.string :as str]))

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

(defn- find-proto-files
  [proto-dir]
  (->> (file-seq (io/file proto-dir))
       (filter #(str/ends-with? (.getName %) ".proto"))
       (map #(.getPath %))))

(defn gen-proto
  "Generate Clojure source from .proto files for a component.

   Opts: {:component \"schema\" :proto-dir \"proto\" :out-dir \"src/gen\"}

   When called via deps/prep-lib the working directory is the component
   root, so pass :base-path \".\" to use paths relative to that directory.
   When called from the workspace root (e.g. gen-all-proto), omit
   :base-path and supply :component instead."
  [{:keys [component proto-dir out-dir base-path]
    :or {proto-dir "proto" out-dir "src/gen"}}]
  (let [root (or base-path (str "components/" component))
        proto-path (str root "/" proto-dir)
        out-path (str root "/" out-dir)
        protos (find-proto-files proto-path)]
    (when (empty? protos)
      (throw (ex-info "No .proto files found" {:path proto-path})))
    (.mkdirs (io/file out-path))
    (b/process {:command-args (concat ["protoc"
                                       "--clojure_out" out-path
                                       "--proto_path" proto-path]
                                      protos)})))

(defn gen-all-proto
  "Discover and generate all proto files across the workspace"
  [_]
  (->> (file-seq (io/file "components"))
       (filter #(= (.getName %) "proto"))
       (filter #(.isDirectory %))
       (map #(-> (.getParent %)
                 (str/split #"/")
                 last))
       (run! #(gen-proto {:component %}))))

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
