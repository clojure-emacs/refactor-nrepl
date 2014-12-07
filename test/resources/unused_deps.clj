(ns resources.unused-deps
  (:require [clojure edn instant
             [string :as str :refer [join]]]
            [clojure.tools.reader :as r]
            [clojure.test :refer :all]
            [clojure.tools.analyzer.passes.jvm
             [box :refer [box]]
             [constant-lifter :refer [constant-lift]]
             [validate-loop-locals :refer [validate-loop-locals]]
             [analyze-host-expr :refer [analyze-host-expr]]])
  (:import java.util.Deque
           java.util.FormattableFlags
           java.io.PushbackReader
           [java.util Date Calendar]))

(clojure.edn/read-string "[1]")
(str/split "12" #"a")
(validate-loop-locals 'something)
(r/read-string "1")
(deftest my-test
  (is (= 1 1)))

(java.util.Date.)
(java.util.Calendar/getInstance)
(FormattableFlags/LEFT_JUSTIFY)
