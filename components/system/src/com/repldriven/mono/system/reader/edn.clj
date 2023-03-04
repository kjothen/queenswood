(ns com.repldriven.mono.system.reader.edn)

(defn system [_ _ value] (keyword (name :donut.system) (name value)))
