(ns refactor-nrepl.rename-file-or-dir-test
  (:require
   [clojure.data :as data]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [refactor-nrepl.core :refer [get-ns-component ns-form-from-string]]
   [refactor-nrepl.rename-file-or-dir :as sut]
   [refactor-nrepl.unreadable-files :refer [ignore-errors?]])
  (:import
   (java.io File)))

(def from-file-path (.getAbsolutePath (File. "testproject/src/com/move/ns_to_be_moved.clj")))
(def to-file-path (.getAbsolutePath (File. "testproject/src/com/move/moved_ns.clj")))
(def from-dir-path (.getAbsolutePath (File. "testproject/src/com/move/")))
(def to-dir-path (.getAbsolutePath (File. "testproject/src/com/moved/")))
(def new-ns-ref "com.move.moved-ns/")
(def old-ns-ref "com.move.ns-to-be-moved/")
(def new-package-prefix "com.move.moved-ns/")
(def old-package-prefix "com.move.ns_to_be_moved/")

(def new-ns-ref-dir "com.moved.ns-to-be-moved/")
(def old-ns-ref-dir "com.move.ns-to-be-moved/")
(def new-package-prefix-dir "com.moved.ns-to-be-moved/")
(def old-package-prefix-dir "com.move.ns_to_be_moved/")

(deftest returns-list-of-affected-files
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [files->absolute-path (fn [files]
                                 (->> files
                                      (map #(-> % io/file .getAbsolutePath))
                                      (into #{})))
          clj ["testproject/src/com/move/dependent_ns1.clj"
               "testproject/src/com/move/subdir/dependent_ns_3.clj"
               "testproject/src/com/move/dependent_ns2.clj"]
          cljs ["testproject/src/com/move/dependent_ns1_cljs.cljs"
                "testproject/src/com/move/subdir/dependent_ns_3_cljs.cljs"]
          common-referencing-files (files->absolute-path (into clj cljs))
          files-referencing-old-ns (conj common-referencing-files from-file-path)
          res (sut/rename-file-or-dir from-file-path to-file-path ignore-errors?)
          [old-file-name new-file-name files-in-both]
          (data/diff files-referencing-old-ns (files->absolute-path res))]
      (is (= old-file-name #{from-file-path}) "That the old filename is not present")
      (is (= new-file-name #{to-file-path}) "That the new filename is present")
      (is (= files-in-both common-referencing-files)
          "That the files referencing the old & the new namespace are the same"))))

(deftest replaces-ns-references-in-dependents
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[^String path content] deps]
                      (when (.endsWith path "clj")
                        (swap! dependents conj content))))]
      (sut/rename-file-or-dir from-file-path to-file-path ignore-errors?)
      (doseq [content @dependents
              :let [ns-form (ns-form-from-string content)
                    require-form (get-ns-component ns-form :require)
                    required-ns (-> require-form second first)
                    import-form (get-ns-component ns-form :import)
                    imported-ns (-> import-form second first)]]
        (is (= 'com.move.moved-ns required-ns))
        (when imported-ns
          (is (= 'com.move.moved_ns imported-ns)))
        (testing content
          (is (not (string/includes? content "ns-to-be-moved"))
              "Renames various types of constructs (metadata, ns-qualified maps, etc)")
          (is (not (string/includes? content "ns_to_be_moved"))
              "Renames emitted class references"))))))

(deftest replaces-fully-qualified-vars-in-dependents
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (sut/rename-file-or-dir from-file-path to-file-path ignore-errors?)
      (doseq [[^String f ^String content] @dependents
              :when (.endsWith f "ns2.clj")]
        (is (.contains content new-ns-ref))
        (is (not (.contains content old-ns-ref)))

        (is (.contains content new-package-prefix))
        (is (not (.contains content old-package-prefix)))))))

(deftest calls-rename-file!-on-the-right-file
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                (fn [old new]
                  (is (= old from-file-path))
                  (is (= new to-file-path)))
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_deps])]
    (sut/rename-file-or-dir from-file-path to-file-path ignore-errors?)))

(deftest replaces-ns-references-in-dependendents-when-moving-dirs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])

                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[^String path content] deps]
                      (when (.endsWith path ".clj")
                        (swap! dependents conj content))))]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (doseq [content @dependents
              :let [ns-form (ns-form-from-string content)
                    require-form (get-ns-component ns-form :require)
                    required-ns (-> require-form second first)
                    import-form (get-ns-component ns-form :import)
                    imported-ns (-> import-form second first)]]
        (is (= 'com.moved.ns-to-be-moved required-ns))
        (when imported-ns
          (is (= 'com.moved.ns_to_be_moved imported-ns)))))))

(deftest returns-list-of-affected-files-when-moving-dirs
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [res (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)]
      (is (seq? res))
      (is (= 8 (count res))))))

(deftest replaces-fully-qualified-vars-in-dependents-when-moving-dirs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (doseq [[^String f ^String content] @dependents
              :when (.endsWith f "ns2.clj")]
        (is (.contains content new-ns-ref-dir))
        (is (not (.contains content old-ns-ref-dir)))

        (is (.contains content new-package-prefix-dir))
        (is (not (.contains content old-package-prefix-dir)))))))

(deftest calls-rename-file!-on-the-right-files-when-moving-dirs
  (let [files (atom [])
        original-files ["testproject/src/com/move/dependent_ns1.clj"
                        "testproject/src/com/move/dependent_ns1_cljs.cljs"
                        "testproject/src/com/move/dependent_ns2.clj"
                        "testproject/src/com/move/dependent_ns2_cljs.cljs"
                        "testproject/src/com/move/non_clj_file"
                        "testproject/src/com/move/ns_to_be_moved.clj"
                        "testproject/src/com/move/ns_to_be_moved_cljs.cljs"
                        "testproject/src/com/move/subdir/dependent_ns_3.clj"
                        "testproject/src/com/move/subdir/dependent_ns_3_cljs.cljs"]]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                  (fn [old new]
                    (swap! files conj [old new]))
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_deps])]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (is (= (count @files) (count original-files)))
      (doseq [[^String old ^String new] @files]
        (is (.contains old "/move/"))
        (is (.contains new "/moved/"))))))

(deftest moves-any-non-clj-files-contained-in-the-dir
  (let [files (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                  (fn [_old new]
                    (swap! files conj new))
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_deps])]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (is (some #(.endsWith ^String % "non_clj_file") @files))
      (is (= 4 (count (filter #(.endsWith ^String % ".cljs") @files)))))))

;;; cljs

(def from-file-path-cljs (.getAbsolutePath (File. "testproject/src/com/move/ns_to_be_moved_cljs.cljs")))
(def to-file-path-cljs (.getAbsolutePath (File. "testproject/src/com/move/moved_ns_cljs.cljs")))

(deftest returns-list-of-affected-files-for-cljs
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [files [to-file-path-cljs
                 "testproject/src/com/move/dependent_ns1_cljs.cljs"
                 "testproject/src/com/move/dependent_ns2_cljs.cljs"
                 "testproject/src/com/move/subdir/dependent_ns_3_cljs.cljs"]
          res (sut/rename-file-or-dir from-file-path-cljs to-file-path-cljs ignore-errors?)]
      (is (or (list? res) (instance? clojure.lang.Cons res)))
      (is (= (count files) (count res)))
      (testing "a .cljs file with string requires in it was not excluded from the rename, and the string requires remain there as-is"
        (let [file-present? (fn [file] (boolean (some #(= % file) files)))
              file-with-string-requires "testproject/src/com/move/dependent_ns2_cljs.cljs"]
          (is (true? (file-present? file-with-string-requires))))))))

(deftest replaces-ns-references-in-dependent-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[_f content] deps]
                      (swap! dependents conj content)))]
      (sut/rename-file-or-dir from-file-path-cljs to-file-path-cljs ignore-errors?)
      (doseq [content @dependents
              :let [ns-form (ns-form-from-string content)
                    require-form (get-ns-component ns-form :require)
                    libspec (second require-form)
                    required-ns (if (sequential? libspec) (first libspec) libspec)]]
        (if (string? required-ns)
          (testing "a .cljs file with string requires in it was not excluded from the rename, and the string requires remain there as-is"
            (is (= "string-require-some-javascript-library" required-ns)))
          (is (= 'com.move.moved-ns-cljs required-ns)))))))

(deftest replaces-fully-qualified-vars-in-dependents-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (sut/rename-file-or-dir from-file-path-cljs to-file-path-cljs ignore-errors?)
      (doseq [[^String f ^String content] @dependents
              :when (.endsWith f "ns2.cljs")]
        (is (.contains content new-ns-ref))
        (is (not (.contains content old-ns-ref)))

        (is (.contains content new-package-prefix))
        (is (not (.contains content old-package-prefix)))))))

(deftest calls-rename-file!-on-the-right-file-for-cljs
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                (fn [old new]
                  (is (= old from-file-path-cljs))
                  (is (= new to-file-path-cljs)))
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_deps])]
    (sut/rename-file-or-dir from-file-path-cljs to-file-path-cljs ignore-errors?)))

(deftest replaces-ns-references-in-dependendents-when-moving-dirs-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])

                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[^String path content] deps]
                      (when (.endsWith path ".cljs")
                        (swap! dependents conj content))))]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (doseq [content @dependents
              :let [ns-form (ns-form-from-string content)
                    require-form (get-ns-component ns-form :require)
                    libspec (second require-form)
                    required-ns (if (sequential? libspec) (first libspec) libspec)
                    require-macros-form (get-ns-component ns-form :require-macros)
                    required-macro-ns (-> require-macros-form second first)]]
        ;; This is a little gross, but the changes are done in two
        ;; passes, so each file has one of them.
        (when require-macros-form
          (is (or (= 'com.moved.ns-to-be-moved-cljs required-ns)
                  (when required-macro-ns
                    (= 'com.moved.ns-to-be-moved required-macro-ns)))))))))

(deftest returns-list-of-affected-files-when-moving-dirs-for-cljs
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [res (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)]
      (is (seq? res))
      (is (= 8 (count res))))))

(deftest replaces-fully-qualified-vars-in-dependents-when-moving-dirs-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [_old _new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (doseq [[^String f ^String content] @dependents
              :when (.endsWith f "ns2.cljs")]
        (is (.contains content new-ns-ref-dir))
        (is (not (.contains content old-ns-ref-dir)))

        (is (.contains content new-package-prefix-dir))
        (is (not (.contains content old-package-prefix-dir)))))))

(deftest calls-rename-file!-on-the-right-files-when-moving-dirs-for-cljs
  (let [files (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                  (fn [old new]
                    (swap! files conj [old new]))
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_deps])]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (is (= (count @files) 9))
      (doseq [[^String old ^String new] @files]
        (is (.contains old "/move/"))
        (is (.contains new "/moved/"))))))

(deftest moves-any-non-cljs-files-contained-in-the-dir-for-cljs
  (let [files (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                  (fn [_old new]
                    (swap! files conj new))
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [_path _old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [_deps])]
      (sut/rename-file-or-dir from-dir-path to-dir-path ignore-errors?)
      (is (some #(.endsWith ^String % "non_clj_file") @files))
      (is (= 4 (count (filter #(.endsWith ^String % ".clj") @files)))))))
