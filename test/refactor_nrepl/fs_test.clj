(ns refactor-nrepl.fs-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]])
  (:import
   (java.io File)))

(require '[refactor-nrepl.fs])

(deftest extensions-are-disabled
  (is (try
        (-> "project.clj" File. .toPath io/file)
        false
        (catch Exception e
          (assert (-> e
                      .getMessage
                      #{"No implementation of method: :as-file of protocol: #'clojure.java.io/Coercions found for class: sun.nio.fs.UnixPath"}))
          true))))
