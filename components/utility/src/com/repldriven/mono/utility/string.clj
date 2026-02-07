(ns com.repldriven.mono.utility.string
  (:import (java.io ByteArrayInputStream)))

(defn string->stream
  "Convert a string to an InputStream with the specified encoding."
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (ByteArrayInputStream.))))
