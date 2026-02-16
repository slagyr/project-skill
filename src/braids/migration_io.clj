(ns braids.migration-io
  (:require [babashka.fs :as fs]
            [braids.migration :as mig]
            [braids.ready-io :as rio]))

(defn- expand-path [path]
  (if (clojure.string/starts-with? path "~/")
    (str (fs/expand-home "~") "/" (subs path 2))
    path))

(defn- real-file-exists? [path]
  (fs/exists? (expand-path path)))

(defn- real-read-file [path]
  (let [p (expand-path path)]
    (when (fs/exists? p) (slurp p))))

(defn- list-iteration-dirs
  "List iteration directory names for a project path."
  [project-path]
  (let [path (expand-path project-path)
        iter-dir (cond
                   (fs/directory? (str path "/.braids/iterations")) (str path "/.braids/iterations")
                   (fs/directory? (str path "/.project/iterations")) (str path "/.project/iterations")
                   :else nil)]
    (when iter-dir
      (->> (fs/list-dir iter-dir)
           (filter fs/directory?)
           (map fs/file-name)
           sort
           vec))))

(defn run-migrate
  "Execute migration. Returns {:actions [...] :report string :dry-run? bool}"
  [{:keys [dry-run?]}]
  (let [state-home (rio/resolve-state-home)
        actions (mig/plan-migration {:state-home state-home
                                     :read-file real-read-file
                                     :file-exists? real-file-exists?})
        ;; Also plan iteration migrations for all registered projects
        reg (rio/load-registry state-home)
        iter-actions (mapcat (fn [{:keys [path]}]
                               (let [iter-dirs (list-iteration-dirs path)]
                                 (when (seq iter-dirs)
                                   (mig/plan-iteration-migrations
                                     {:project-path path
                                      :iteration-dirs iter-dirs
                                      :read-file real-read-file
                                      :file-exists? real-file-exists?}))))
                             (:projects reg))
        all-actions (concat actions iter-actions)]
    (when-not dry-run?
      (doseq [{:keys [path content]} all-actions]
        (let [p (expand-path path)]
          (fs/create-dirs (fs/parent p))
          (spit p content))))
    {:actions (vec all-actions)
     :report (mig/format-migration-report (vec all-actions))
     :dry-run? dry-run?}))
