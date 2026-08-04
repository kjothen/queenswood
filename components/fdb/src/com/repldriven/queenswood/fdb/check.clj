(ns com.repldriven.queenswood.fdb.check
  (:require
    [com.repldriven.queenswood.fdb.transact]

    [com.repldriven.mono.error.interface :as error])
  (:import
    (com.apple.foundationdb.record RecordIndexUniquenessViolation)
    (com.apple.foundationdb.record.metadata MetaDataException)
    (com.repldriven.queenswood.fdb.transact Txn)))

(defn txn?
  [x]
  (instance? Txn x))

(defn- root-cause
  [^Throwable t]
  (if-let [c (.getCause t)]
    (recur c)
    t))

(defn meta-data-already-current?
  [^Throwable t]
  (let [root (root-cause t)]
    (and (instance? MetaDataException root)
         (some? (re-find #"meta-data version must increase"
                         (or (.getMessage root) ""))))))

(defn uniqueness-violation?
  [anomaly]
  (when (error/anomaly? anomaly)
    (loop [ex (:exception (error/payload anomaly))]
      (cond
       (nil? ex)
       false

       (instance? RecordIndexUniquenessViolation ex)
       true

       :else
       (recur (.getCause ex))))))
