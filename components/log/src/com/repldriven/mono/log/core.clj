(ns com.repldriven.mono.log.core
  (:require
    [clojure.tools.logging :as log])
  (:import
    (org.slf4j.bridge SLF4JBridgeHandler)))

;; Install JUL to SLF4J bridge at namespace load time
(SLF4JBridgeHandler/removeHandlersForRootLogger)
(SLF4JBridgeHandler/install)

(defmacro info [& args] `(log/info ~@args))

(defmacro infof [& args] `(log/infof ~@args))

(defmacro warn [& args] `(log/warn ~@args))

(defmacro warnf [& args] `(log/warnf ~@args))

(defmacro error [& args] `(log/error ~@args))

(defmacro errorf [& args] `(log/errorf ~@args))
