(ns braids.iteration-io
  (:require [babashka.fs :as fs]
            [babashka.process :as proc]
            [cheshire.core :as json]
            [clojure.string :as str]
            [braids.iteration :as iter]
            [braids.orch-io :as oio]
            [braids.ready-io :as rio]))

(defn- expand-path [path]
  (if (str/starts-with? path "~/")
    (str (fs/expand-home "~") "/" (subs path 2))
    path))

(defn load-all-beads
  "Run `bd list --json` in the project directory and parse the result."
  [project-path]
  (let [path (expand-path project-path)]
    (try
      (let [result (proc/shell {:dir path :out :string :err :string}
                               "bd" "list" "--all" "--json")
            parsed (json/parse-string (:out result))]
        (if (sequential? parsed) parsed []))
      (catch Exception _e []))))

(defn load-and-show
  "Load iteration data for a project and format it."
  [{:keys [project-path json?]}]
  (let [path (expand-path project-path)
        iter-num (oio/find-active-iteration project-path)]
    (if-not iter-num
      "No active iteration found."
      (let [iter-md (slurp (str path "/.project/iterations/" iter-num "/ITERATION.md"))
            number (or (iter/parse-iteration-number iter-md) iter-num)
            status (or (iter/parse-iteration-status iter-md) "unknown")
            stories (iter/parse-iteration-stories iter-md)
            beads (load-all-beads project-path)
            annotated (iter/annotate-stories stories beads)
            stats (iter/completion-stats annotated)
            data {:number number :status status :stories annotated :stats stats}]
        (if json?
          (iter/format-iteration-json data)
          (iter/format-iteration data))))))
