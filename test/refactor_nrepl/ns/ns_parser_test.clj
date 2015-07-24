(ns refactor-nrepl.ns.ns-parser-test
  (:require [refactor-nrepl.ns.ns-parser :refer :all]
            [clojure.test :refer :all]))

(deftest parses-libspecs-with-prefix-vectors-containing-specs
  (is (= (list {:ns 'compojure.core :refer '[defroutes]}
               {:ns 'compojure.route :as 'route}
               {:ns 'website.github-manager :as 'gman})
         (get-libspecs '(ns refactor-nrepl.test
                          (:require [compojure
                                     [core :refer [defroutes]]
                                     [route :as route]]
                                    [website.github-manager :as gman])
                          (:gen-class))))))

(deftest parses-libspecs-with-prefix-vectors-without-specs
  (is (= (list {:ns 'compojure.core}
               {:ns 'compojure.route})
         (get-libspecs '(ns refactor-nrepl.test
                          (:require [compojure core route]))))))

(deftest parse-imports-without-prefix-list
  (is (= (list 'java.util.Date 'java.util.Calendar)
         (get-imports '(ns refactor-nrepl.ns.ns-parser-test
                         (:require [refactor-nrepl.ns.ns-parser :refer :all]
                                   [clojure.test :refer :all])
                         (:import java.util.Date java.util.Calendar))))))

(deftest parses-imports-with-prefix-list
  (is (= (list 'java.util.Date 'java.util.Calendar)
         (get-imports '(ns refactor-nrepl.ns.ns-parser-test
                         (:require [refactor-nrepl.ns.ns-parser :refer :all]
                                   [clojure.test :refer :all])
                         (:import [java.util Date Calendar]))))))
