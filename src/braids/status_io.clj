(ns braids.status-io
  (:require [braids.iteration-io :as iio]
            [braids.orch-io :as oio]
            [braids.ready-io :as rio]
            [braids.iteration :as iter]
            [braids.status :as status]))

(defn load-iteration-data
  "Load iteration data for a project: number, status, annotated stories, stats."
  [project-path]
  (let [iter-num (oio/find-active-iteration project-path)]
    (when iter-num
      (let [path (if (clojure.string/starts-with? project-path "~/")
                   (str (babashka.fs/expand-home "~") "/" (subs project-path 2))
                   project-path)
            iter-md (slurp (str path "/.project/iterations/" iter-num "/ITERATION.md"))
            number (or (iter/parse-iteration-number iter-md) iter-num)
            status (or (iter/parse-iteration-status iter-md) "unknown")
            stories (iter/parse-iteration-stories iter-md)
            beads (iio/load-all-beads project-path)
            annotated (iter/annotate-stories stories beads)
            stats (iter/completion-stats annotated)]
        {:number number :status status :stories annotated :stats stats}))))

(defn load-and-status
  "Load all data and produce dashboard."
  [{:keys [project-slug json? session-labels]
    :or {session-labels []}}]
  (let [state-home (rio/resolve-state-home)
        reg (rio/load-registry state-home)
        all-projects (:projects reg)
        configs (into {} (map (fn [{:keys [slug path]}]
                                [slug (rio/load-project-config path)])
                              all-projects))
        iterations (into {} (keep (fn [{:keys [slug path]}]
                                    (when-let [data (load-iteration-data path)]
                                      [slug data]))
                                  (filter #(= :active (:status %)) all-projects)))
        workers (rio/count-workers session-labels)
        dash (status/build-dashboard reg configs iterations workers)]
    (if project-slug
      ;; Single project detail
      (let [proj (first (filter #(= project-slug (:slug %)) (:projects dash)))]
        (if proj
          (if json?
            (status/format-dashboard-json {:projects [proj]})
            (status/format-project-detail proj))
          (str "Project not found: " project-slug)))
      ;; Full dashboard
      (if json?
        (status/format-dashboard-json dash)
        (status/format-dashboard dash)))))
