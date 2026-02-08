(ns com.repldriven.mono.db.interface
  (:require
   com.repldriven.mono.db.system.core

   [next.jdbc]))

(defn get-datasource
  "Get a JDBC datasource from a datasource config map."
  [datasource-config]
  (next.jdbc/get-datasource datasource-config))
