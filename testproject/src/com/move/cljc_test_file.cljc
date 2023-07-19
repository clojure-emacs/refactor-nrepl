(ns com.move.cljc-test-file
  (:require  #?(:clj [clj-namespace-from.cljc-file :as foo]
                :cljs [cljs-namespace-from.cljc-file :as bar :include-macros true])))

(declare something-or-other)
