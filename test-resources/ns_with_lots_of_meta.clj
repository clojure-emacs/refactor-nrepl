(ns ^:md1 ^:md2 ^{:longhand "as well" :really? "yes" :ok true}
 ns-with-gen-class-methods-meta
  (:gen-class
   :methods [^:static [foo [String] String]
             ^{:test true} [bar [String] String]
             ^{:other "text"} [baz [String] String]]
   :name Name)
  (:require
   [clojure.string :as s]
   [clojure.pprint :refer [fresh-line]]
   [clojure.edn :as edn]))

(defn useit
  []
  (fresh-line))

(defn issue-239-metadata-based-protocol-extension []
  ;; https://github.com/clojure-emacs/refactor-nrepl/issues/239
  ^{`s/whatever-goes true} {})
