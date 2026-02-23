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
        out-path (str root "/src/gen")
        protos (proto-files proto-path)]
    (when (empty? protos)
      (throw (ex-info "No .proto files found" {:path proto-path})))
    (.mkdirs (io/file out-path))
    (b/process {:command-args (concat ["protoc"
                                       "--clojure_out" out-path
                                       "--proto_path" proto-path]
                                      protos)})))
