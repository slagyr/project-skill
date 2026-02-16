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

(defn run-migrate
  "Execute migration. Returns {:actions [...] :report string :dry-run? bool}"
  [{:keys [dry-run?]}]
  (let [state-home (rio/resolve-state-home)
        actions (mig/plan-migration {:state-home state-home
                                     :read-file real-read-file
                                     :file-exists? real-file-exists?})]
    (when-not dry-run?
      (doseq [{:keys [path content]} actions]
        (let [p (expand-path path)]
          (fs/create-dirs (fs/parent p))
          (spit p content))))
    {:actions actions
     :report (mig/format-migration-report actions)
     :dry-run? dry-run?}))
