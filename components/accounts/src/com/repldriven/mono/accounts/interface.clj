(ns com.repldriven.mono.accounts.interface
  (:require
    com.repldriven.mono.accounts.system

    [com.repldriven.mono.accounts.watcher :as watcher]))

(def handle-changelog-change watcher/handle-change)
