(ns dev.local)

(comment
  ;; open a debug portal
  (require '[portal.api :as p])
  (def p (p/open))
  (add-tap #'p/submit) ; Add portal as a tap> target
  (tap> "Opened tap")
  (p/close))
