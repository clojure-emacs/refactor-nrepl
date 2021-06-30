(ns ns-with-rename-cleaned
  "This is a docstring for the ns

  It contains an \"escaped string\"."
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [[binomial [int int] double]])
  (:require [clojure.string :refer :all :rename {replace foo} :reload-all true]))
