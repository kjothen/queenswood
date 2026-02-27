(ns com.repldriven.mono.accounts.system.core
  (:require
    [com.repldriven.mono.accounts.system.components :as components]

    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :accounts {:processor components/processor})
