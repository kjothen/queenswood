(ns com.repldriven.mono.fdb.watcher
  (:require
    [com.repldriven.mono.fdb.changelog :as changelog]))

(defn start
  "Starts a daemon thread polling process-changelog with the
  given handler. Config keys: record-db, record-store,
  consumer-id, store-name, handler (2-arity fn [store record]).
  Returns {:stop fn}."
  [config]
  (let [running (atom true)
        {:keys [record-db record-store consumer-id store-name handler]} config
        t (doto (Thread. (fn []
                           (while @running
                             (try (changelog/process record-db
                                                     record-store
                                                     consumer-id
                                                     store-name
                                                     handler)
                                  (catch Exception _))
                             (try (when @running (Thread/sleep 100))
                                  (catch InterruptedException _
                                    (reset! running false))))))
            (.setDaemon true)
            (.setName (str consumer-id "-thread"))
            (.start))]
    {:stop (fn [] (reset! running false) (.interrupt t) (.join t 5000))}))
