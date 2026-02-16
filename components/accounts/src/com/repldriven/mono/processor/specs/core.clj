(ns com.repldriven.mono.processor.specs.core
  (:require
   [com.repldriven.mono.processor.specs.account-lifecycle :as account-lifecycle]))

(def specs
  (merge
   account-lifecycle/specs))
