(ns braids.config
  "Pure functions for braids config.edn management.")

(def defaults
  {:braids-home "~/Projects"})

(defn parse-config
  "Parse config EDN string. Returns map with defaults applied."
  [s]
  (merge defaults (read-string s)))

(defn serialize-config
  "Serialize config map to EDN string."
  [config]
  (pr-str config))
