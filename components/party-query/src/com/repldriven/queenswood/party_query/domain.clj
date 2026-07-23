(ns com.repldriven.queenswood.party-query.domain
  (:require
    [clojure.string :as str]))

(defn- normalize-name
  [s]
  (-> (or s "")
      str/trim
      str/lower-case
      (str/replace #"\s+" " ")))

(defn- tokenize
  [s]
  (set (str/split s #"\s+")))

(defn match-name
  [party-name query-name]
  (let [a (normalize-name party-name)
        b (normalize-name query-name)]
    (cond
     (= a b)
     :match

     (let [ta (tokenize a)
           tb (tokenize b)
           shorter (if (<= (count ta) (count tb)) ta tb)
           longer (if (<= (count ta) (count tb)) tb ta)]
       (every? longer shorter))
     :close-match

     :else
     :no-match)))
