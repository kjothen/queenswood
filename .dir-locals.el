;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")

((clojure-mode . ((mode . zprint-format-on-save)
                  (projectile-project-type . clojure-cli)
                  (cider-preferred-build-tool . clojure-cli)
                  (cider-clojure-cli-aliases . "-Sforce -M:inspect/portal-cli:repl/rebel:env/dev:dev:test"))))
