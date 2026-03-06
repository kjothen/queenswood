(ns com.repldriven.mono.utility.vars)

(defn vname
  [v]
  (-> v
      meta
      :name
      str))
