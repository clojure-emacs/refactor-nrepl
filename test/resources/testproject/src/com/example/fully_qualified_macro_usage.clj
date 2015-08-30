(ns com.example.fully-qualified-macro-usage
  (:require com.example.macro-def
            com.example.macro-def-cljc))

(defn fully-qualified-macro-usage []
  (com.example.macro-def/my-macro :fully-qualified)
  (com.example.macro-def-cljc/cljc-macro :fully-qualified))
