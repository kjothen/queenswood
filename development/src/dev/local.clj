(ns com.repldriven.mono.dev.local)

(comment
  ;; open a debug portal
  (require '[portal.api :as p])
  (def p (p/open))
  (add-tap #'p/submit)
  (tap> "Opened tap")
  ;(p/close))

(comment
  (require '[com.repldriven.mono.iam-api.main :as iam-api])
  (iam-api/-main "-c" "bases/iam-api/test-resources/iam-api/test-env.edn"
                 "-p" "dev")
  (iam-api/stop!)
  ;
)
