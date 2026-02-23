(ns build
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.build.api :as b]))

(defn- proto-files
  [proto-dir]
  (->> (file-seq (io/file proto-dir))
       (filter #(str/ends-with? (.getName %) ".proto"))
       (map #(.getPath %))))

(defn gen-proto
  [{:keys [root] :or {root "."}}]
  (let [proto-path (str root "/resources")
        clj-out (str root "/src/gen/clojure")
        java-out (str root "/src/gen/java")
        class-out (str root "/classes")
        protos (proto-files proto-path)]
    (when (empty? protos)
      (throw (ex-info "No .proto files found" {:path proto-path})))
    (run! #(.mkdirs (io/file %)) [clj-out java-out class-out])
    (b/process {:command-args (concat ["protoc"
                                       "--clojure_out" clj-out
                                       "--java_out" java-out
                                       "--proto_path" proto-path]
                                      protos)})
    (b/javac {:src-dirs [java-out]
              :class-dir class-out
              :basis (b/create-basis {:project (str root "/deps.edn")})})))
