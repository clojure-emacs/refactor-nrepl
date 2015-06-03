(ns com.example.fully-qualified-macro-usage
  (:require com.example.macro-def))

(defn fully-qualified-macro-usage []
  (com.example.macro-def/my-macro :fully-qualified))
