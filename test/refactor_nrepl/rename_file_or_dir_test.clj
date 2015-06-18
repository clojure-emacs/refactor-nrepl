(ns refactor-nrepl.rename-file-or-dir-test
  (:require [clojure.test :refer :all]
            [refactor-nrepl.ns.helpers
             :refer [get-ns-component ns-form-from-string]]
            [refactor-nrepl.rename-file-or-dir :refer :all])
  (:import [java.io File PushbackReader StringReader]))

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
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [dependents])]
    (let [res (rename-file-or-dir from-file-path to-file-path)]
      (is (or (list? res) (instance? clojure.lang.Cons res)))
      (is (count res) 3))))

(deftest replaces-ns-references-in-dependents
  (let [dependents (atom [])]
    (with-redefs [refactor-nrepl.rename-file-or-dir/rename-file! (fn [old new])
                  refactor-nrepl.rename-file-or-dir/update-ns! (fn [path old-ns])
                  refactor-nrepl.rename-file-or-dir/update-dependents!
                  (fn [deps]
                    (doseq [[f content] deps]
                      (swap! dependents conj content)))]
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
                    (doseq [[f content] deps]
                      (swap! dependents conj content)))]
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
                refactor-nrepl.rename-file-or-dir/update-dependents! (fn [dependents])]
    (let [res (rename-file-or-dir from-dir-path to-dir-path)]
      (is (seq? res))
      (is (count res) 3))))

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
      (is (= (count @files) 4))
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
      (is (first (filter #(.endsWith % "non_clj_file") @files))))))
