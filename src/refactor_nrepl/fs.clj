(ns refactor-nrepl.fs
  "Sets the compile-time feature-flags from the `fs` library.
  This ns should be `require`d before any other `fs` ns."
  (:require
   [me.raynes.fs.feature-flags]))

(alter-var-root #'me.raynes.fs.feature-flags/extend-coercions? (constantly false))
