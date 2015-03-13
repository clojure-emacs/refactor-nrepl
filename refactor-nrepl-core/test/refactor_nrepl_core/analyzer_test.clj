(ns refactor-nrepl-core.analyzer-test
  (:require [refactor-nrepl-core.analyzer :refer :all]
            [dynapath.util :as dp]
            [clojure.test :refer :all])
  (:import java.io.File))

(deftest test-find-unbound-vars
  (dp/add-classpath-url (.. Thread currentThread getContextClassLoader)
                        (.toURL (File. "resources/testproject/src/com/example/test_unbound.clj")))
  (is (= '#{}
         (find-unbound-vars 'refactor-nrepl-core.analyzer-test)))
  (is (= '#{s sep}
         (find-unbound-vars 'com.example.test-unbound))))
