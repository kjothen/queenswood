(ns com.repldriven.mono.error.interface
  (:require
    [de.otto.nom.core :as nom]

    [clojure.test :refer [is]]))

(defn- error-anomaly? [x] (and (vector? x) (= :error/anomaly (first x))))

(defmethod nom/abominable? error-anomaly? [_] true)
(defmethod nom/adapt error-anomaly? [x] x)

;; Predicates
(defn anomaly? [x] (error-anomaly? x))

;; Creation
(defn fail
  [category & more]
  (let [p (cond (map? (first more)) (first more)
                (string? (first more)) {:message (first more)}
                (seq more) (apply hash-map more)
                :else {})]
    [:error/anomaly category p]))

;; Introspection
(defn kind [x] (when (anomaly? x) (second x)))
(defn payload [x] (when (anomaly? x) (get x 2 {})))

;; Test assertions
(defn refute-nom
  "Fails the test if value is an anomaly, printing its kind and message."
  [v]
  (is (not (anomaly? v))
      (format "Unexpected anomaly [%s]: %s"
              (kind v)
              (or (:message (payload v)) (pr-str v)))))

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
(defmacro nom-do>
  "Execute operations sequentially, short-circuiting on the first anomaly.
  If any returns an anomaly, call error-fn with it."
  [ops error-fn]
  (let [bindings (vec (mapcat (fn [op] [`_# op]) ops))]
    `(let [result# (nom/let-nom ~bindings :ok)]
       (when (anomaly? result#) (~error-fn result#)))))

(defmacro nom-let>
  "Execute let-nom> bindings (every binding anomaly-checked). If the result is
  an anomaly, call error-fn with it. Returns the result."
  [bindings error-fn]
  `(let [result# (nom/let-nom> ~bindings nil)]
     (when (anomaly? result#) (~error-fn result#))
     result#))
