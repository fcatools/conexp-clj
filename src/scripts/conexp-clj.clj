;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(use 'clojure.tools.cli)

;;

(defn- run-repl []
  (clojure.main/repl :init #(use 'conexp.main)))

(let [options (cli *command-line-args*
                   (optional ["--load" "Load a given script"]))]
  (when (options :load)
    (use 'conexp.main)
    (load-file (options :load)))
  (when-not (or (options :load))
    (clojure.main/repl :init #(do (use 'conexp.main) (use 'clojure.repl)))))

;;

nil
