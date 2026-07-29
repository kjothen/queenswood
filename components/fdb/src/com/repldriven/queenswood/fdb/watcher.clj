(ns com.repldriven.queenswood.fdb.watcher
  (:require
    [com.repldriven.queenswood.fdb.changelog :as changelog]))

(defn start
  "Starts a daemon thread polling process-changelog with the
  given handler. Config keys: record-db, consumer-id,
  store-name, handler (2-arity fn [ctx changelog-bytes]),
  keyspace-prefix (optional). Returns {:stop fn}."
  [config]
  (let [running (atom true)
        {:keys [record-db consumer-id store-name handler keyspace-prefix]}
        config
        t (doto (Thread. (fn []
                           (while @running
                             (try (changelog/process
                                   record-db
                                   consumer-id
                                   store-name
                                   handler
                                   {:keyspace-prefix keyspace-prefix})
                                  (catch Exception _))
                             (try (when @running (Thread/sleep 100))
                                  (catch InterruptedException _
                                    (reset! running false))))))
            (.setDaemon true)
            (.setName (str consumer-id "-thread"))
            (.start))]
    {:stop (fn [] (reset! running false) (.interrupt t) (.join t 5000))}))
