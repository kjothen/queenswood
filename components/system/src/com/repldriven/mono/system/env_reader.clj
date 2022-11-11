(ns com.repldriven.mono.system.env-reader)

(defn system [_ _ value] (keyword (name :donut.system) (name value)))
