(ns com.repldriven.mono.processor.spec.account-lifecycle)

(def ^:private open-account
  "Schema for open-account command data."
  [:map
   ["account-id" :string]
   ["name" :string]
   ["currency" :string]])

(def ^:private close-account
  "Schema for close-account command data."
  [:map
   ["account-id" :string]])

(def ^:private reopen-account
  "Schema for reopen-account command data."
  [:map
   ["account-id" :string]])

(def ^:private suspend-account
  "Schema for suspend-account command data."
  [:map
   ["account-id" :string]])

(def ^:private unsuspend-account
  "Schema for unsuspend-account command data."
  [:map
   ["account-id" :string]])

(def ^:private archive-account
  "Schema for archive-account command data."
  [:map
   ["account-id" :string]])

(def specs
  {"open-account" open-account
   "close-account" close-account
   "reopen-account" reopen-account
   "suspend-account" suspend-account
   "unsuspend-account" unsuspend-account
   "archive-account" archive-account})
