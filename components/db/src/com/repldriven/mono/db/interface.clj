(ns com.repldriven.mono.db.interface
  (:require
    com.repldriven.mono.db.system.core

    [com.repldriven.mono.error.interface :as error]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def as-unqualified-lower-maps rs/as-unqualified-lower-maps)

(defn get-datasource
  "Get a JDBC datasource from a datasource config map."
  [datasource-config]
  (error/try-nom :db/datasource
                 "Failed to get datasource"
                 (next.jdbc/get-datasource datasource-config)))

(defn execute-one!
  "Execute a SQL statement and return a single result.
  Wraps next.jdbc/execute-one! with error handling."
  ([datasource sql-params]
   (error/try-nom :db/execute-one
                  "Failed to execute SQL statement"
                  (next.jdbc/execute-one! datasource sql-params)))
  ([datasource sql-params opts]
   (error/try-nom :db/execute-one
                  "Failed to execute SQL statement"
                  (next.jdbc/execute-one! datasource sql-params opts))))

(defn execute!
  "Execute a SQL statement and return all results.
  Wraps next.jdbc/execute! with error handling."
  ([datasource sql-params]
   (error/try-nom :db/execute
                  "Failed to execute SQL statement"
                  (next.jdbc/execute! datasource sql-params)))
  ([datasource sql-params opts]
   (error/try-nom :db/execute
                  "Failed to execute SQL statement"
                  (next.jdbc/execute! datasource sql-params opts))))

(def update-count :next.jdbc/update-count)
