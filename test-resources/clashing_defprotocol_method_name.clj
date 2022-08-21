(ns clashing-defprotocol-method-name
  "Exercises that var shadowing (in this case: for `update`) doesn't cause any issue.")

(defprotocol Repository
  "Store and retrieve."
  :extend-via-metadata true
  (add [repository insert-params]
    "Add to the repository.")
  (update [repository update-params]
    "Update."))
