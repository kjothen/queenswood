(ns com.repldriven.mono.test-system.interface
  (:require
    [com.repldriven.mono.test-system.core :as core]))

(defmacro with-test-system
  {:clj-kondo/lint-as 'clojure.core/let}
  [binding & body]
  `(core/with-test-system ~binding ~@body))

