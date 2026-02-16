(ns braids.orch-io
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [braids.orch :as orch]
            [braids.ready-io :as rio]))

(defn parse-iteration-status-edn
  "Extract the status from iteration.edn content string.
   Returns the status string (lowercase) or nil."
  [content]
  (try
    (when-let [parsed (edn/read-string content)]
      (when-let [s (:status parsed)]
        (name s)))
    (catch Exception _ nil)))

(defn find-active-iteration
  "Scan .braids/iterations/*/iteration.edn for one with :status :active.
   Returns the iteration number (directory name) or nil."
  [project-path]
  (let [path (if (str/starts-with? project-path "~/")
               (str (fs/expand-home "~") "/" (subs project-path 2))
               project-path)
        iter-dir (cond
                   (fs/directory? (str path "/.braids/iterations")) (str path "/.braids/iterations")
                   (fs/directory? (str path "/.project/iterations")) (str path "/.project/iterations")
                   :else (str path "/.braids/iterations"))]
    (when (fs/exists? iter-dir)
      (some (fn [dir]
              (let [iter-edn (str dir "/iteration.edn")]
                (when (fs/exists? iter-edn)
                  (let [content (slurp iter-edn)]
                    (when (= "active" (parse-iteration-status-edn content))
                      (fs/file-name dir))))))
            (sort (fs/list-dir iter-dir))))))

(defn gather-and-tick
  "Full IO pipeline for orch tick: load everything, compute spawn decisions."
  ([] (gather-and-tick {}))
  ([{:keys [braids-home state-home session-labels]
     :or {session-labels []}}]
   (let [home (or state-home (rio/resolve-state-home))
         reg (rio/load-registry home)
         active-projects (filter #(= :active (:status %)) (:projects reg))
         configs (into {} (map (fn [{:keys [slug path]}]
                                 [slug (rio/load-project-config path)])
                               active-projects))
         iterations (into {} (keep (fn [{:keys [slug path]}]
                                     (when-let [iter (find-active-iteration path)]
                                       [slug iter]))
                                   active-projects))
         beads (into {} (map (fn [{:keys [slug path]}]
                               [slug (if (contains? iterations slug)
                                       (rio/load-ready-beads path)
                                       [])])
                             active-projects))
         workers (rio/count-workers session-labels)
         notifications (into {} (map (fn [{:keys [slug]}]
                                       [slug (select-keys (get configs slug)
                                                          [:notifications :notification-mentions])])
                                     active-projects))]
     (orch/tick reg configs iterations beads workers notifications))))
