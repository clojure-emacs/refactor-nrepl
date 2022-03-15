(ns as-alias
  (:require
   [foo :as-alias f]
   [unused :as-alias clean-me-up]))

(def bar ::f/bar)
