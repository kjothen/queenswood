(ns com.repldriven.mono.iam.spec
  (:refer-clojure :exclude [name])
  (:require
   [clojure.string :as str]))

;;;; rfc types
;;;;

(def rfc1035
  {:re "[a-z][-a-z0-9]{4,28}[a-z0-9]"
   :desc
   "must be 6 to 30 lowercase letters, digits, or hyphen.
Must start with a letter.
Trailing hyphens are prohibited"})


(def rfc5322
  {:re "[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}"
   :desc "must be a valid RFC 5322 email address"})

;;;; common field types
;;;;

(def description {:re ".{256}" :desc "must be maximum of 256 UTF-8 bytes"})
(def display-name {:desc "must be maximum of 100 UTF-8 bytes"})
(def email-address rfc5322)
(def project-id rfc1035)
(def project-name
  {:re (str "projects/" (:re project-id))
   :desc "must be `projects/{PROJECT_ID}`"})
(def unique-id {:re "\\d{21}" :desc "must be 21 digits"})

;;;; common functions
;;;;

(defn re-pattern-exact [s] (re-pattern (str "^" s "$")))

;;;; common types
;;;;

(def Description [:string {:max 256 :error/message (:desc description)}])
(def DisplayName [:string {:max 100 :error/message (:desc display-name)}])
(def ProjectId
  [:re {:error/message (:desc project-id)} (re-pattern-exact (:re project-id))])
(def ProjectName
  [:re {:error/message (:desc project-name)}
   (re-pattern-exact (:re project-name))])
(def UniqueId
  [:re {:error/message (:desc unique-id)} (re-pattern-exact (:re unique-id))])
