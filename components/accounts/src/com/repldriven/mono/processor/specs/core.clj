(ns com.repldriven.mono.processor.specs.core
  (:require
    [com.repldriven.mono.processor.specs.account-lifecycle :as
     account-lifecycle]
    [com.repldriven.mono.processor.specs.reporting-operations :as
     reporting-operations]))

(def specs (merge account-lifecycle/specs reporting-operations/specs))
