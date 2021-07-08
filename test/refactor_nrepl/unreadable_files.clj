(ns refactor-nrepl.unreadable-files
  "Helpers and a self-assertion for dealing with unreadable files in a given codebase."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]))

(def ignore-errors?
  "Currently errors while analysing the classpath (most relevantly our `test-resources` dir) are ignored.

  This is because some files in that dir are intentionally invalid, which intentionally represents a use case
  (e.g. users can have WIP code, files not meant to be vanilla namespaces, etc)."
  true)

(def ignore-errors-str
  (pr-str ignore-errors?))

;; This is more of an assertion about the test suite (namely that certain files exist in the claspath and are problematic)
;; than an actual unit test (as there's no SUT)
(deftest readable-file?
  (doseq [file (->> ["unreadable_file.clj"
                     "readable_file_incorrect_aliases.clj"
                     "readable_file_incorrect_data_readers.clj"]
                    (map (comp io/as-file io/resource)))]
    (is (try
          (-> file slurp read-string)
          false
          (catch Exception _
            true))
        "All these files are unreadable. And yet the refactor-nrepl test suite will pass
(it would have failed prior to the introduction of `#'refactor-nrepl.util/with-suppressed-errors`).")))
