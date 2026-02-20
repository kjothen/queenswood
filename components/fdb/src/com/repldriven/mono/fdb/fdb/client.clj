(ns com.repldriven.mono.fdb.fdb.client
  (:refer-clojure :exclude [get set])
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn set
  "Set a string key-value pair in the FDB database."
  [{:keys [db]} ^String key ^String value]
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
  [{:keys [db]} ^String key]
  (error/try-nom :fdb/get
                 {:message "Failed to get value" :key key}
                 (.run db
                       (reify
                        java.util.function.Function
                          (apply [_ tr]
                            (some-> (.get tr (.getBytes key))
                                    .join
                                    (String.)))))))
