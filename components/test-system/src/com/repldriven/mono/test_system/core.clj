(ns com.repldriven.mono.test-system.core
  (:require
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :refer [is]]))

(defmacro with-test-system
  {:clj-kondo/lint-as 'clojure.core/let}
  [[sym config] & body]
  (let [[config-file patch-fn] (if (vector? config) config [config nil])]
    (if patch-fn
      `(system/with-system [~sym
                            (error/nom-> (env/config ~config-file :test)
                                         system/defs
                                         ~(list patch-fn)
                                         system/start)]
         (is (system/system? ~sym))
         ~@body)
      `(system/with-system [~sym
                            (error/nom-> (env/config ~config-file :test)
                                         system/defs
                                         system/start)]
         (is (system/system? ~sym))
         ~@body))))
