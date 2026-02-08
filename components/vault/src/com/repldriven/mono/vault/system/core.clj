(ns com.repldriven.mono.vault.system.core
  (:require
   [com.repldriven.mono.vault.system.components :as components]

   [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :vault
  {:client components/client})
