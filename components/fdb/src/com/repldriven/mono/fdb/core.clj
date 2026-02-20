(ns com.repldriven.mono.fdb.core
  (:refer-clojure :exclude [get set])
  (:require
    com.repldriven.mono.fdb.system.core

    [com.repldriven.mono.error.interface :as error])
  (:import
    (com.apple.foundationdb Database)))

(defn set
  "Set a string key-value pair in the FDB database."
  [^Database db ^String key ^String value]
  (error/try-nom
   :fdb/set
   {:message "Failed to set value" :key key}
   (.run db
         (reify
          java.util.function.Function
            (apply [_ tr] (.set tr (.getBytes key) (.getBytes value)) nil)))))

(defn get
  "Get a string value by key from the FDB database.
  Returns nil if the key does not exist."
  [^Database db ^String key]
  (error/try-nom :fdb/get
                 {:message "Failed to get value" :key key}
                 (.run db
                       (reify
                        java.util.function.Function
                          (apply [_ tr]
                            (some-> (.get tr (.getBytes key))
                                    .join
                                    (String.)))))))
