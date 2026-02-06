(ns com.repldriven.mono.sql.interface
  (:require [com.repldriven.mono.sql.system.core :as system]
            [next.jdbc]))

(defn configure-system [config] (system/configure config))

(defn get-datasource
  "Get a JDBC datasource from a datasource config map."
  [datasource-config]
  (next.jdbc/get-datasource datasource-config))
