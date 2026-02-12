(ns com.repldriven.mono.migrator.liquibase
  (:require
    [next.jdbc]

    [com.repldriven.mono.error.interface :as error]
    [clojure.java.io :as io])
  (:import
    (liquibase Contexts LabelExpression Liquibase)
    (liquibase.database DatabaseFactory)
    (liquibase.database.jvm JdbcConnection)
    (liquibase.resource DirectoryResourceAccessor)
    (java.io File)))

(defn- resource-accessor
  "Create a DirectoryResourceAccessor from a classpath resource path.
   Resolves the resource to its filesystem location."
  [resource-path]
  (let [resource (io/resource resource-path)
        file (io/file (.toURI resource))
        dir (.getParentFile file)]
    (DirectoryResourceAccessor. dir)))

(defn migrate
  [db-spec resource-path]
  (error/try-nom :migrator/migration-failed
                 "Failed to run database migrations"
                 (with-open [conn (next.jdbc/get-connection db-spec)]
                   (let [jdbc-connection (JdbcConnection. conn)
                         database (.findCorrectDatabaseImplementation
                                   (DatabaseFactory/getInstance)
                                   jdbc-connection)
                         accessor (resource-accessor resource-path)
                         filename (.getName (File. ^String resource-path))
                         lb (Liquibase. ^String filename accessor database)]
                     (.update lb (Contexts.) (LabelExpression.))))))
