;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")

((clojure-mode . ((mode . zprint-format-on-save)
                  (projectile-project-type . clojure-cli)
                  (cider-preferred-build-tool . clojure-cli)
                  (cider-clojure-cli-aliases . ":lib/pretty-errors:repl/rebel:dev:test -Sforce"))))
