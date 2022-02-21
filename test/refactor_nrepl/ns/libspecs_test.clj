(ns refactor-nrepl.ns.libspecs-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [are deftest is testing]]
   [refactor-nrepl.ns.libspecs :as sut])
  (:import
   (java.io File)))

(def example-file
  (-> "foo/ns/libspecs.clj" io/resource io/as-file))

(def other-similarly-named-file
  (-> "bar/ns/libspecs.clj" io/resource io/as-file))

(def unreadable-file
  (-> "unreadable_file.clj" io/resource io/as-file))

(deftest add-tentative-aliases-test

  (testing "`ignore-errors?`"
    (let [files [unreadable-file]]
      (is (thrown? Exception (sut/add-tentative-aliases {} :clj files false)))
      (is (= {}
             (sut/add-tentative-aliases {} :clj files true)))))

  (are [desc base input expected] (testing desc
                                    (is (= expected
                                           (sut/add-tentative-aliases base :clj input false)))
                                    true)
    #_base                        #_input                                   #_expected
    "Doesn't remove existing aliases"
    {'foo [`bar]}                 []                                        {'foo [`bar]}

    "Adds the two possible aliases for the given namespace"
    {'foo [`bar]}                 [example-file]                            '{foo         [refactor-nrepl.ns.libspecs-test/bar]
                                                                              libspecs    [foo.ns.libspecs]
                                                                              ns.libspecs [foo.ns.libspecs]}

    "When an existing alias overlaps with a suggested alias,
the original one is kept and no other semantic is suggested
(this way, any given alias will point to one namespace at most)"
    {'libspecs [`other]}          [example-file]                            '{libspecs    [refactor-nrepl.ns.libspecs-test/other],
                                                                              ns.libspecs [foo.ns.libspecs]}

    "If a namespace is already aliased, no extra aliases are suggested at all"
    {'example '[foo.ns.libspecs]} [example-file]                            '{example [foo.ns.libspecs]}

    "If a namespace is only aliased as `sut`, extra aliases are suggested as usual"
    {'sut '[foo.ns.libspecs]}     [example-file]                            '{sut         [foo.ns.libspecs],
                                                                              libspecs    [foo.ns.libspecs],
                                                                              ns.libspecs [foo.ns.libspecs]}

    "If two files can result in the same alias being suggested, both will be included"
    {}                            [example-file other-similarly-named-file] '{libspecs    [foo.ns.libspecs
                                                                                           bar.ns.libspecs],
                                                                              ns.libspecs [foo.ns.libspecs
                                                                                           bar.ns.libspecs]}))

(deftest namespace-aliases-test
  (testing "Runs without errors"
    (let [{:keys [clj cljs]} (sut/namespace-aliases true [(File. "test-resources")])]
      (is (seq clj))
      (is (seq cljs)))))
