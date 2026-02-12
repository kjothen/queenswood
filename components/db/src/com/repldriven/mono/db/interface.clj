(ns com.repldriven.mono.db.interface
  (:require
   com.repldriven.mono.db.system.core

   [com.repldriven.mono.error.interface :as error]
   [next.jdbc]))

(defn get-datasource
  "Get a JDBC datasource from a datasource config map."
  [datasource-config]
  (error/try-nom :db/datasource-failed
                 "Failed to get datasource"
                 (next.jdbc/get-datasource datasource-config)))
