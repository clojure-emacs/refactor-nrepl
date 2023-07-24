(ns refactor-nrepl.ns.imports-and-refers-analysis-test
  (:require
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.ns.imports-and-refers-analysis :as sut]))

(deftest works

  (is (= '#{java.lang.Thread}
         (sut/candidates :import 'Thread [] {})))

  (is (-> (sut/candidates :import 'Thread [] {}) first meta :refactor-nrepl/is-class))

  (is (= '#{java.io.File}
         (sut/candidates :import 'File [] {})))

  (is (contains? #{'#{com.sun.tools.javac.util.List java.awt.List java.util.List}
                   '#{com.sun.xml.internal.bind.v2.schemagen.xmlschema.List java.awt.List java.util.List}}
                 (sut/candidates :import 'List [] {})))

  (is (contains? '#{#{clojure.core}
                    #{clojure.core cljs.core}}
                 (sut/candidates :refer 'areduce [] {}))))
