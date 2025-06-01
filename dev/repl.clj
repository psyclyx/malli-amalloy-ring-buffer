;; requires deps from `:libs`, `:dev`, `:nrepl`, and `:cider`
(ns repl
  (:require
    [cider.piggieback :as piggieback]
    [cljs.repl.node :as node]))


(defn start-cljs
  []
  (piggieback/cljs-repl (node/repl-env)))


(comment
  (start-cljs))
