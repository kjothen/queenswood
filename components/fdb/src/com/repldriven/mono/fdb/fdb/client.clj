(ns com.repldriven.mono.fdb.fdb.client
  (:refer-clojure :exclude [get set])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (com.apple.foundationdb Database
                            FDB)))

(defn create
  "Create FDB client and database instance from configuration.

  Config map must contain:
  - :cluster-file-path - absolute path to FDB cluster file"
  [{:keys [cluster-file-path api-version]}]
  (let [api-version (or api-version 730)]
    (log/info "Creating FDB client with cluster file:" cluster-file-path
              "api-version:" api-version)
    (error/try-nom :fdb/create
                   {:message "Failed to create FDB database"
                    :cluster-file-path cluster-file-path}
                   (let [_ (log/info "Selecting FDB API version:" api-version)
                         fdb (FDB/selectAPIVersion api-version)
                         _ (log/info "Opening FDB database...")
                         db (.open fdb cluster-file-path)]
                     (log/info "Opened FDB database with cluster file:"
                               cluster-file-path
                               "db:" db)
                     db))))

(defn close
  "Close FDB database instance."
  [^Database db]
  (when (some? db) (log/info "Closing FDB database") (.close db)))

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
