(ns com.repldriven.queenswood.fdb.kv
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]])
  (:import
    (com.apple.foundationdb Database)
    (java.util.function Function)))

(defn set-str
  [^Database db ^String key ^String value]
  (try-nom
   :fdb/set-str
   {:message "Failed to set value" :key key}
   (.run db
         ^Function (fn [tr] (.set tr (.getBytes key) (.getBytes value)) nil))))

(defn get-str
  [^Database db ^String key]
  (try-nom :fdb/get-str
           {:message "Failed to get value" :key key}
           (.run db
                 ^Function
                 (fn [tr]
                   (some-> (.get tr (.getBytes key))
                           .join
                           (String.))))))

(defn set-bytes
  [^Database db ^String key ^bytes value]
  (try-nom
   :fdb/set-bytes
   {:message "Failed to set bytes" :key key}
   (.run db ^Function (fn [tr] (.set tr (.getBytes key) value) nil))))

(defn get-bytes
  [^Database db ^String key]
  (try-nom :fdb/get-bytes
           {:message "Failed to get bytes" :key key}
           (.run db
                 ^Function
                 (fn [tr]
                   (some-> (.get tr (.getBytes key))
                           .join)))))
