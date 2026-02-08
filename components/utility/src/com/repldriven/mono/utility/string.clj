(ns com.repldriven.mono.utility.string
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import (java.io ByteArrayInputStream)))

(defn string->stream
  "Convert a string to an InputStream with the specified encoding."
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (ByteArrayInputStream.))))

(defn resolve-source
  "Resolve a source string to a resource. Handles classpath: prefix."
  [source]
  (if (str/starts-with? source "classpath:")
    (io/resource (subs source (count "classpath:")))
    source))
