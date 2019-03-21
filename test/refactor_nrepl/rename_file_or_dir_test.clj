(ns refactor-nrepl.rename-file-or-dir-test
  (:require [clojure.test :refer :all]
            [refactor-nrepl.core
             :refer
             [get-ns-component ns-form-from-string]]
            [refactor-nrepl.rename-file-or-dir :refer :all])
  (:import java.io.File))

(def from-file-path (.getAbsolutePath (File. "test/resources/testproject/src/com/move/ns_to_be_moved.clj")))
(def to-file-path (.getAbsolutePath (File. "test/resources/testproject/src/com/move/moved_ns.clj")))
(def from-dir-path (.getAbsolutePath (File. "test/resources/testproject/src/com/move/")))
(def to-dir-path (.getAbsolutePath (File. "test/resources/testproject/src/com/moved/")))
(def new-ns-ref "com.move.moved-ns/")
(def old-ns-ref "com.move.ns-to-be-moved/")
(def new-package-prefix "com.move.moved-ns/")
(def old-package-prefix "com.move.ns_to_be_moved/")

(def new-ns-ref-dir "com.moved.ns-to-be-moved/")
(def old-ns-ref-dir "com.move.ns-to-be-moved/")
(def new-package-prefix-dir "com.moved.ns-to-be-moved/")
(def old-package-prefix-dir "com.move.ns_to_be_moved/")

(deftest returns-list-of-affected-files
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [res (rename-file-or-dir from-file-path to-file-path)]
      (is (or (list? res) (instance? clojure.lang.Cons res)))
      (is (= 4 (count res))))));; currently not tracking :require-macros!!

(deftest replaces-ns-references-in-dependents
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[path content] deps]
                      (when (.endsWith path "clj")
                        (swap! dependents conj content))))]
      (rename-file-or-dir from-file-path to-file-path)
      (doseq [content @dependents
              :let [ns-form (ns-form-from-string content)
                    require-form (get-ns-component ns-form :require)
                    required-ns (-> require-form second first)
                    import-form (get-ns-component ns-form :import)
                    imported-ns (-> import-form second first)]]
        (is (= 'com.move.moved-ns required-ns))
        (when imported-ns
          (is (= 'com.move.moved_ns imported-ns)))))))

(deftest replaces-fully-qualified-vars-in-dependents
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (rename-file-or-dir from-file-path to-file-path)
      (doseq [[f content] @dependents
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
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [deps])]
    (rename-file-or-dir from-file-path to-file-path)))

(deftest replaces-ns-references-in-dependendents-when-moving-dirs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])

                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[path content] deps]
                      (when (.endsWith path ".clj")
                        (swap! dependents conj content))))]
      (rename-file-or-dir from-dir-path to-dir-path)
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
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [res (rename-file-or-dir from-dir-path to-dir-path)]
      (is (seq? res))
      (is (= 8 (count res))))))

(deftest replaces-fully-qualified-vars-in-dependents-when-moving-dirs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (rename-file-or-dir from-dir-path to-dir-path)
      (doseq [[f content] @dependents
              :when (.endsWith f "ns2.clj")]
        (is (.contains content new-ns-ref-dir))
        (is (not (.contains content old-ns-ref-dir)))

        (is (.contains content new-package-prefix-dir))
        (is (not (.contains content old-package-prefix-dir)))))))

(deftest calls-rename-file!-on-the-right-files-when-moving-dirs
  (let [files (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                  (fn [old new]
                    (swap! files conj [old new]))
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [deps])]
      (rename-file-or-dir from-dir-path to-dir-path)
      (is (= (count @files) 9))
      (doseq [[old new] @files]
        (is (.contains old "/move/"))
        (is (.contains new "/moved/"))))))

(deftest moves-any-non-clj-files-contained-in-the-dir
  (let [files (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                  (fn [old new]
                    (swap! files conj new))
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [deps])]
      (rename-file-or-dir from-dir-path to-dir-path)
      (is (some #(.endsWith % "non_clj_file") @files))
      (is (= 4 (count (filter #(.endsWith % ".cljs") @files)))))))


;;; cljs


(def from-file-path-cljs (.getAbsolutePath (File. "test/resources/testproject/src/com/move/ns_to_be_moved_cljs.cljs")))
(def to-file-path-cljs (.getAbsolutePath (File. "test/resources/testproject/src/com/move/moved_ns_cljs.cljs")))

(deftest returns-list-of-affected-files-for-cljs
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [res (rename-file-or-dir from-file-path-cljs to-file-path-cljs)]
      (is (or (list? res) (instance? clojure.lang.Cons res)))
      (is (= 4 (count res))))))

(deftest replaces-ns-references-in-dependent-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj content)))]
      (rename-file-or-dir from-file-path-cljs to-file-path-cljs)
      (doseq [content @dependents
              :let [ns-form (ns-form-from-string content)
                    require-form (get-ns-component ns-form :require)
                    libspec (second require-form)
                    required-ns (if (sequential? libspec) (first libspec) libspec)]]
        (is (= 'com.move.moved-ns-cljs required-ns))))))

(deftest replaces-fully-qualified-vars-in-dependents-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (rename-file-or-dir from-file-path-cljs to-file-path-cljs)
      (doseq [[f content] @dependents
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
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [deps])]
    (rename-file-or-dir from-file-path-cljs to-file-path-cljs)))

(deftest replaces-ns-references-in-dependendents-when-moving-dirs-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])

                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[path content] deps]
                      (when (.endsWith path ".cljs")
                        (swap! dependents conj content))))]
      (rename-file-or-dir from-dir-path to-dir-path)
      (doseq [content @dependents
              :let [ns-form (ns-form-from-string content)
                    require-form (get-ns-component ns-form :require)
                    libspec (second require-form)
                    required-ns (if (sequential? libspec) (first libspec) libspec)
                    require-macros-form (get-ns-component ns-form :require-macros)
                    required-macro-ns (-> require-macros-form second first)]]
        ;; This is a little gross, but the changes are done in two
        ;; passes, so each file has one of them.
        (is (or (= 'com.moved.ns-to-be-moved-cljs required-ns)
                (when required-macro-ns
                  (= 'com.moved.ns-to-be-moved required-macro-ns))))))))

(deftest returns-list-of-affected-files-when-moving-dirs-for-cljs
  (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [dependents])
                refactor-nrepl.rename-file-or-dir/file-or-symlink-exists? (constantly true)]
    (let [res (rename-file-or-dir from-dir-path to-dir-path)]
      (is (seq? res))
      (is (= 8 (count res))))))

(deftest replaces-fully-qualified-vars-in-dependents-when-moving-dirs-for-cljs
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj [f content])))]
      (rename-file-or-dir from-dir-path to-dir-path)
      (doseq [[f content] @dependents
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
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [deps])]
      (rename-file-or-dir from-dir-path to-dir-path)
      (is (= (count @files) 9))
      (doseq [[old new] @files]
        (is (.contains old "/move/"))
        (is (.contains new "/moved/"))))))

(deftest moves-any-non-cljs-files-contained-in-the-dir-for-cljs
  (let [files (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file!
                  (fn [old new]
                    (swap! files conj new))
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents! (fn [deps])]
      (rename-file-or-dir from-dir-path to-dir-path)
      (is (some #(.endsWith % "non_clj_file") @files))
      (is (= 4 (count (filter #(.endsWith % ".clj") @files)))))))
