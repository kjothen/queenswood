(ns com.repldriven.mono.log.interface
  (:require
    [com.repldriven.mono.log.core :as core]))

(defn init ([]) ([profile]))

(defmacro info [& args] `(core/info ~@args))

(defmacro infof [& args] `(core/infof ~@args))

(defmacro warn [& args] `(core/warn ~@args))

(defmacro warnf [& args] `(core/warnf ~@args))

(defmacro error [& args] `(core/error ~@args))

(defmacro errorf [& args] `(core/errorf ~@args))
