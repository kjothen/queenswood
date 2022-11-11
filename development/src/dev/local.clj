(ns com.repldriven.mono.dev.local)

(comment
  ;; open a debug portal
  (require '[portal.api :as p])
  (def p (p/open))
  (add-tap #'p/submit)
  (tap> "Opened tap")
  (p/close))
