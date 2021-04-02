(ns refactor-nrepl.ns.rebuild-test
  (:require [clojure.test :as t]
            [refactor-nrepl.ns.rebuild :as rebuild]))

(t/deftest build-cljc-dep-forms
  (t/testing "shared :requires only, no conditionals"
    (t/is (= '((:require [clojure.string :as str]))
             (rebuild/build-cljc-dep-forms
              '{:clj            {:require ({:ns clojure.string, :as str, :rename {}})
                                 :import  ()}
                :cljs           {:require ({:ns clojure.string, :as str, :rename {}})
                                 :import  ()}
                :source-dialect :cljc}))))
  (t/testing "shared :requires *and* :cljs conditionals"
    (t/is (= (list (symbol "#?@") '(:clj
                                    [(:require [clojure.string :as str])]
                                    :cljs
                                    [(:require [clojure.string :as str])
                                     (:require-macros [conditional-with-require-macros :refer [my-when]])]))
             (rebuild/build-cljc-dep-forms
              '{:clj            {:require ({:ns clojure.string, :as str, :rename {}})
                                 :import  ()}
                :cljs           {:require        ({:ns clojure.string, :as str, :rename {}})
                                 :import         ()
                                 :require-macros ({:ns conditional-with-require-macros, :refer (my-when), :rename {}})},
                :source-dialect :cljc}))))

  (t/testing "should rebuild cljc ns declarations that consist of only conditionals for a single language correctly (#296)"
    (t/is (= (list (symbol "#?@")
                   '(:cljs [(:require-macros [conditional-with-require-macros :refer [my-when]])]))
             (rebuild/build-cljc-dep-forms
              '{:clj            {:require ()
                                 :import  ()}
                :cljs           {:require        ()
                                 :import         ()
                                 :require-macros ({:ns conditional-with-require-macros, :refer (my-when), :rename {}})}
                :source-dialect :cljc}))))

  (t/testing "Empty declaration should be rebuilt as-is"
    (t/is (= nil
             (rebuild/build-cljc-dep-forms
              '{:clj            {:require ()
                                 :import  ()}
                :cljs           {:require ()
                                 :import  ()}
                :source-dialect :cljc})))))
