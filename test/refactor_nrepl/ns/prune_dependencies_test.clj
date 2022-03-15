(ns refactor-nrepl.ns.prune-dependencies-test
  (:require
   [clojure.test :refer [are is deftest]]
   [refactor-nrepl.ns.prune-dependencies :as sut]))

(deftest libspec-in-use-without-refer-all?
  (is      (@#'sut/libspec-in-use-without-refer-all? '{:as-alias foo}  "foo/bar"))
  (is (not (@#'sut/libspec-in-use-without-refer-all? '{:as-alias quux} "foo/bar"))))

(deftest imports->namespaces
  (are [input expected] (= expected
                           (sut/imports->namespaces input))
    ['java.io.File]              #{'java.io}
    ['my_ns.File]                #{'my-ns}
    ['[java.io File]]            #{'java.io}
    ['[java.io File FileReader]] #{'java.io}
    ['(java.io File)]            #{'java.io}
    ['java.io.File
     'my_ns.File
     '[java.io File]
     '(java.io File)]            #{'java.io 'my-ns}))

(deftest libspec->namespaces
  (are [input expected] (= expected
                           (sut/libspec->namespaces input))
    'foo.bar
    '[foo.bar],

    '[foo.bar]
    '[foo.bar],

    '[foo.bar :as bar]
    '[foo.bar],

    '[clojure data edn
      [instant :as inst :reload true]
      [pprint :refer [cl-format formatter get-pretty-writer]]
      [string :refer :all :reload-all true]
      [test :refer :all]
      [walk :refer [postwalk prewalk]]
      xml]
    '[clojure.data clojure.edn clojure.instant clojure.pprint clojure.string clojure.test clojure.walk clojure.xml]))

(deftest imports-contain-libspec?
  (are [imports libspec expected] (= expected
                                     (sut/imports-contain-libspec? (sut/imports->namespaces imports)
                                                                   libspec))
    #_imports             #_libspec               #_expected
    '[]                   'foo.bar                false
    '[foo.bar.SomeType]   'foo.bar                true
    '[foo_bar.SomeType]   'foo-bar                true
    '[[foo.bar SomeType]] 'foo.bar                true
    '[[foo_bar SomeType]] 'foo-bar                true
    '[foo.bar.SomeType]   'foo.baz                false
    '[]                   '[foo.bar]              false
    '[foo.bar.SomeType]   '[foo.bar]              true
    '[foo.bar.SomeType]   '[foo.bar :as f]        true
    '[[foo.bar SomeType]] '[foo.bar]              true
    '[[foo_bar SomeType]] '[foo-bar :as f]        true
    '[foo_bar.SomeType]   '[foo-bar :as f]        true
    '[[foo_bar SomeType]] '[foo-bar]              true
    '[[foo_bar SomeType]] '[foo-bar :as f]        true
    '[foo.bar.SomeType]   '[foo.baz]              false
    '[clojure.data.Data]  '[clojure data]         true
    '[clojure.data.Data]  '[clojure [data :as d]] true
    '[clojure.data.Data]  '[clojure foo]          false))
