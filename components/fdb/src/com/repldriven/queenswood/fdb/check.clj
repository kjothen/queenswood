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

(defn- causes
  [^Throwable t]
  (take-while some? (iterate #(.getCause ^Throwable %) t)))

(defn meta-data-already-current?
  [^Throwable t]
  (boolean (some (fn [^Throwable c]
                   (and (instance? MetaDataException c)
                        (some? (re-find #"meta-data version must increase"
                                        (or (.getMessage c) "")))))
                 (causes t))))

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
