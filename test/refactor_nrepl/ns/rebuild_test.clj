(ns refactor-nrepl.ns.rebuild-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [refactor-nrepl.ns.rebuild :as sut]))

(t/deftest build-cljc-dep-forms
  (t/testing "shared :requires only, no conditionals"
    (t/is (= '((:require [clojure.string :as str]))
             (sut/build-cljc-dep-forms
              '{:clj            {:require ({:ns clojure.string, :as str, :rename {}})
                                 :import  ()}
                :cljs           {:require ({:ns clojure.string, :as str, :rename {}})
                                 :import  ()}
                :source-dialect :cljc}))))
  (t/testing "single segment namespaces"
    (t/is (= '((:require [single-segment-ns]))
             (sut/build-cljc-dep-forms
              '{:clj            {:require ({:ns single-segment-ns :rename {}})
                                 :import  ()}
                :cljs           {:require ({:ns single-segment-ns, :rename {}})
                                 :import  ()}
                :source-dialect :cljc}))))
  (t/testing "shared :requires *and* :cljs conditionals"
    (t/is (= (list (symbol "#?@") '(:clj
                                    [(:require [clojure.string :as str])]
                                    :cljs
                                    [(:require [clojure.string :as str])
                                     (:require-macros [conditional-with-require-macros :refer [my-when]])]))
             (sut/build-cljc-dep-forms
              '{:clj            {:require ({:ns clojure.string, :as str, :rename {}})
                                 :import  ()}
                :cljs           {:require        ({:ns clojure.string, :as str, :rename {}})
                                 :import         ()
                                 :require-macros ({:ns conditional-with-require-macros, :refer (my-when), :rename {}})},
                :source-dialect :cljc}))))

  (t/testing "should rebuild cljc ns declarations that consist of only conditionals for a single language correctly (#296)"
    (t/is (= (list (symbol "#?@")
                   '(:cljs [(:require-macros [conditional-with-require-macros :refer [my-when]])]))
             (sut/build-cljc-dep-forms
              '{:clj            {:require ()
                                 :import  ()}
                :cljs           {:require        ()
                                 :import         ()
                                 :require-macros ({:ns conditional-with-require-macros, :refer (my-when), :rename {}})}
                :source-dialect :cljc}))))

  (t/testing "Empty declaration should be rebuilt as-is"
    (t/is (= nil
             (sut/build-cljc-dep-forms
              '{:clj            {:require ()
                                 :import  ()}
                :cljs           {:require ()
                                 :import  ()}
                :source-dialect :cljc}))))

  (testing "`:as-alias`"
    (let [input '{:require ({:ns foo, :as-alias str})
                  :import  ()}]
      (t/testing "is kept"
        (t/is (= '((:require [foo :as-alias str]))
                 (sut/build-cljc-dep-forms
                  {:clj            input
                   :cljs           input
                   :source-dialect :cljc})))))))

(deftest rebuild-ns-form
  (let [input '(ns refactor-nrepl.ns.clean-ns (:require [foo :as-alias f]))]
    (is (= input (sut/rebuild-ns-form '{:clj {:require ({:ns foo, :as-alias f})
                                              :import nil}
                                        :ns refactor-nrepl.ns.clean-ns
                                        :source-dialect :clj}
                                      input))
        "`:as-alias` directives are kept")))
