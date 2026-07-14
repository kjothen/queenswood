(ns com.repldriven.mono.widget.system
  (:require
    [com.repldriven.mono.widget.commands :as commands]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance (commands/->WidgetProcessor config)))

   :system/config
   {:record-db    system/required-component
    :record-store system/required-component}

   :system/instance-schema some?})

;; TODO: register `processor` via system/defcomponents
