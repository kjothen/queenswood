(ns com.repldriven.mono.accounts.interface
  (:require
    com.repldriven.mono.accounts.system.core

    [com.repldriven.mono.accounts.watcher :as watcher]))

(def handle-changelog-change watcher/handle-change)
