(ns rename-spec
  (:require [speclj.core :refer :all]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def project-root (System/getProperty "user.dir"))

(defn completed-iteration? [path]
  (let [rel (str (fs/relativize project-root path))]
    (and (str/starts-with? rel ".project/iterations/")
         (let [iter-num (some-> (re-find #"iterations/(\d+)" rel) second)]
           (when iter-num
             (let [iter-md (str project-root "/.project/iterations/" iter-num "/ITERATION.md")]
               (when (fs/exists? iter-md)
                 (str/includes? (slurp (str iter-md)) "complete"))))))))

(defn mutable-files
  "All text files excluding completed iterations, .git, .beads, and this spec."
  [root]
  (->> (fs/glob root "**")
       (filter fs/regular-file?)
       (filter (fn [p]
                 (let [name (str (fs/file-name p))]
                   (some #(str/ends-with? name %) [".md" ".clj" ".edn"]))))
       (remove (fn [p]
                 (let [rel (str (fs/relativize root p))]
                   (or (str/starts-with? rel ".git/")
                       (str/starts-with? rel ".beads/")
                       (str/includes? rel "rename_spec")))))
       (remove completed-iteration?)))

(defn files-containing [root pattern]
  (->> (mutable-files root)
       (filter (fn [f]
                 (let [content (slurp (str f))]
                   (re-find pattern content))))
       (map #(str (fs/relativize root %)))))

(describe "Skill rename: projects → braids"

  (it "skill directory is braids/ not projects/"
    (should (fs/directory? (str project-root "/braids")))
    (should-not (fs/directory? (str project-root "/projects"))))

  (it "SKILL.md frontmatter name is 'braids'"
    (let [skill-md (slurp (str project-root "/braids/SKILL.md"))
          name-line (some->> (str/split-lines skill-md)
                             (filter #(re-find #"^name:" %))
                             first)]
      (should-contain "braids" name-line)))

  (it "no mutable files reference 'skills/projects' path"
    (let [matches (files-containing project-root #"skills/projects")]
      (should= [] matches)))

  (it "no mutable files reference '~/.openclaw/projects' state path (except migration.md)"
    (let [matches (->> (files-containing project-root #"~/\.openclaw/projects[/\s\)]")
                       (remove #(str/includes? % "migration.md")))]
      (should= [] matches)))

  (it "no mutable files reference 'the projects skill'"
    (let [matches (files-containing project-root #"(?i)projects skill")]
      (should= [] matches)))

  (it "README install instructions reference braids/"
    (let [readme (slurp (str project-root "/README.md"))]
      (should-contain "braids" readme)
      (should= [] (files-containing project-root #"skills/projects"))))

  (it "cron job prompt references braids"
    (let [skill-md (slurp (str project-root "/braids/SKILL.md"))]
      (should (re-find #"skills/braids/references/orchestrator\.md" skill-md)))))

(run-specs)
