(ns com.repldriven.mono.migrator.liquibase
  (:require [next.jdbc])
  (:import (liquibase Contexts LabelExpression Liquibase)
           (liquibase.database DatabaseFactory)
           (liquibase.database.jvm JdbcConnection)
           (liquibase.resource ClassLoaderResourceAccessor)))

(defn migrate
  [db-spec resource-path]
  (with-open [conn (next.jdbc/get-connection db-spec)]
    (let [jdbc-connection (JdbcConnection. conn)
          database (.findCorrectDatabaseImplementation
                     (DatabaseFactory/getInstance)
                     jdbc-connection)
          lb (Liquibase. ^String resource-path
               (ClassLoaderResourceAccessor.) database)]
      (.update lb (Contexts.) (LabelExpression.)))))
