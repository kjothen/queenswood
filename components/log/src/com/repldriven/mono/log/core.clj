(ns com.repldriven.mono.log.core
  (:require [clojure.tools.logging :as log]))

(defmacro info
  [& args]
  `(log/info ~@args))

(defmacro infof
  [& args]
  `(log/infof ~@args))

(defmacro warn
  [& args]
  `(log/warn ~@args))

(defmacro warnf
  [& args]
  `(log/warnf ~@args))

(defmacro error
  [& args]
  `(log/error ~@args))

(defmacro errorf
  [& args]
  `(log/errorf ~@args))
