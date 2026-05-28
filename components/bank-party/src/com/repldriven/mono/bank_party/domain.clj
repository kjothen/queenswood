(ns com.repldriven.mono.bank-party.domain
  (:refer-clojure :exclude [type])
  (:require
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.string :as str]))

(defn new-party
  [data]
  (let [{:keys [bank-id type display-name]} data
        now (System/currentTimeMillis)
        status (if (= :party-type-person type)
                 :party-status-pending
                 :party-status-active)]
    {:bank-id bank-id
     :party-id (utility/generate-id "pty")
     :type type
     :display-name display-name
     :status status
     :created-at now
     :updated-at now}))

(defn activate-party
  [party]
  (assoc party
         :status :party-status-active
         :updated-at (System/currentTimeMillis)))

(defn reject-party
  [party]
  (assoc party
         :status :party-status-rejected
         :updated-at (System/currentTimeMillis)))

(defn new-party-national-identifier
  [national-identifier bank-id party-id]
  (let [{:keys [type value issuing-country]} national-identifier]
    {:bank-id bank-id
     :party-id party-id
     :type type
     :value value
     :issuing-country issuing-country
     :created-at (System/currentTimeMillis)}))

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

