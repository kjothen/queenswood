(ns com.repldriven.mono.error.interface
  (:require
    [de.otto.nom.core :as nom]))

;; Predicates
(defn anomaly? [x] (nom/anomaly? x))

;; Creation
(defn fail [category & more] (apply nom/fail category more))

;; Introspection
(defn kind [x] (nom/kind x))

;; Threading macros
(defmacro nom->
  {:clj-kondo/lint-as 'clojure.core/->}
  [& forms]
  `(nom/nom-> ~@forms))

(defmacro nom->>
  {:clj-kondo/lint-as 'clojure.core/->>}
  [& forms]
  `(nom/nom->> ~@forms))

;; Let bindings
(defmacro let-nom
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(nom/let-nom ~bindings ~@body))

(defmacro let-nom>
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(nom/let-nom> ~bindings ~@body))

;; Context wrapper
(defmacro with-nom [& body] `(nom/with-nom ~@body))

;; Exception catching
(defmacro try-nom
  [category message & body]
  `(try ~@body
        (catch Exception e#
          (fail ~category {:message ~message :exception e#}))))

(defmacro try-nom-ex
  "Like try-nom but catches a specific exception type."
  [category exception-type message & body]
  `(try ~@body
        (catch ~exception-type e#
          (fail ~category {:message ~message :exception e#}))))

;; Side-effect error handling
(defmacro with-anomaly?
  "Execute operations sequentially. If any returns an anomaly, call error-fn with it."
  [ops error-fn]
  (let [bindings (vec (mapcat (fn [op] [`_# op]) ops))]
    `(let [result# (nom/let-nom ~bindings :ok)]
       (when (nom/anomaly? result#) (~error-fn result#)))))

(defmacro with-let-anomaly?
  "Execute let-nom bindings. If the result is an anomaly, call error-fn with it."
  [bindings error-fn]
  `(let [result# (nom/let-nom ~bindings nil)]
     (when (nom/anomaly? result#) (~error-fn result#))
     result#))
