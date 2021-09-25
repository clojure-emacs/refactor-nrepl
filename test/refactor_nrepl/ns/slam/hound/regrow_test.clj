(ns refactor-nrepl.ns.slam.hound.regrow-test
  (:require
   [refactor-nrepl.ns.slam.hound.regrow :as sut]
   [clojure.test :refer [deftest is]]))

(deftest works
  (is (contains? '#{#{java.lang.Thread sun.jvm.hotspot.runtime.Thread}
                    #{java.lang.Thread}}
                 (sut/candidates :import 'Thread [] {})))

  (is (contains? '#{#{clojure.core}
                    #{clojure.core cljs.core}}
                 (sut/candidates :refer 'areduce [] {}))))
