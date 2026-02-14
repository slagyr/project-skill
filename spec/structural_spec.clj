(ns structural-spec
  (:require [spec-helper :refer [describe context it should should-not should= should-contain should-match]]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def home (System/getProperty "user.home"))
(def projects-home (or (System/getenv "PROJECTS_HOME") (str home "/Projects")))
(def skill-symlink (str home "/.openclaw/skills/projects"))
(def skill-source (str projects-home "/projects-skill/projects"))
(def registry (str projects-home "/registry.md"))

(defn slurp-safe [path] (when (fs/exists? path) (slurp path)))
(defn symlink? [path] (fs/sym-link? path))
(defn dir? [path] (fs/directory? path))
(defn file? [path] (fs/exists? path))

(defn real-path [path]
  (try (str (fs/real-path path)) (catch Exception _ nil)))

;; ── Skill Symlink ──

(describe "Skill Symlink"
  (it "symlink exists"
    (symlink? skill-symlink))
  (it "symlink target is valid directory"
    (dir? skill-symlink))
  (it "symlink points to projects-skill/projects"
    (= (real-path skill-symlink) (real-path skill-source))))

;; ── Skill Directory ──

(describe "Skill Directory"
  (it "SKILL.md exists"
    (file? (str skill-symlink "/SKILL.md")))
  (it "references/ directory exists"
    (dir? (str skill-symlink "/references")))
  (doseq [ref ["orchestrator.md" "worker.md" "agents-template.md"
                "status-dashboard.md" "migration.md"]]
    (it (str "references/" ref " exists")
      (file? (str skill-symlink "/references/" ref)))))

;; ── SKILL.md Format ──

(describe "SKILL.md Format"
  (let [content (slurp-safe (str skill-symlink "/SKILL.md"))]
    (it "has YAML frontmatter"
      (str/starts-with? (or content "") "---"))
    (it "frontmatter has name field"
      (should-contain "name:" content))
    (it "frontmatter has description field"
      (should-contain "description:" content))))

;; ── Registry ──

(describe "Registry"
  (it "registry.md exists"
    (file? registry))

  (let [content (slurp-safe registry)]
    (it "has table header"
      (should-contain "| Slug | Status | Priority | Path |" content))

    ;; Parse and validate each project
    (let [lines (str/split-lines (or content ""))
          data-lines (->> lines
                         (filter #(str/includes? % "|"))
                         (drop 2) ;; header + separator
                         (remove #(str/starts-with? (str/trim %) "|-")))]
      (doseq [line data-lines]
        (let [cols (->> (str/split line #"\|")
                        (map str/trim)
                        (remove str/blank?))
              [slug status priority path] cols
              resolved (str/replace (or path "") "~" home)]
          (when (and slug (not= slug "Slug") (not (str/starts-with? slug "-")))

            (describe (str "Project: " slug)
              (it "directory exists"
                (dir? resolved))
              (it "PROJECT.md exists"
                (file? (str resolved "/PROJECT.md")))
              (it "AGENTS.md exists"
                (file? (str resolved "/AGENTS.md")))
              (it ".beads/ directory exists"
                (dir? (str resolved "/.beads")))
              (it "iterations/ directory exists"
                (dir? (str resolved "/iterations")))
              (it "is a git repo"
                (dir? (str resolved "/.git")))

              ;; PROJECT.md fields
              (let [pmd (slurp-safe (str resolved "/PROJECT.md"))]
                (it "PROJECT.md has Status field"
                  (should-match #"(?im)(^\- \*\*Status:\*\*|^Status:)" pmd))
                (it "PROJECT.md has Goal section"
                  (should-contain "## Goal" pmd))
                (it "PROJECT.md has Guardrails section"
                  (should-contain "## Guardrails" pmd)))

              ;; Registry status validation
              (it (str "registry status is valid (" status ")")
                (contains? #{"active" "paused" "blocked"} status))
              (it (str "registry priority is valid (" priority ")")
                (contains? #{"high" "normal" "low"} priority))

              ;; Iteration validation
              (when (dir? (str resolved "/iterations"))
                (doseq [iter-dir (sort (fs/list-dir (str resolved "/iterations")))]
                  (let [iter-name (str (fs/file-name iter-dir))
                        iter-md (str iter-dir "/ITERATION.md")]
                    (when (and (fs/directory? iter-dir) (re-matches #"\d{3}" iter-name))
                      (if (file? iter-md)
                        (let [icontent (slurp iter-md)
                              iter-status (some-> (re-find #"(?i)Status:\*\*\s*(.*)|Status:\s*(.*)" icontent)
                                                  rest
                                                  (->> (remove nil?) first str/trim))]
                          (it (str "iterations/" iter-name "/ITERATION.md has Status")
                            (should-match #"(?i)Status:" icontent))
                          (it (str "iterations/" iter-name "/ITERATION.md has Stories section")
                            (should-contain "## Stories" icontent))
                          (it (str "iterations/" iter-name " status is valid (" iter-status ")")
                            (contains? #{"planning" "active" "complete"} iter-status))
                          (when (= iter-status "complete")
                            (it (str "iterations/" iter-name " has RETRO.md")
                              (file? (str iter-dir "/RETRO.md"))))
                          (when (= iter-status "active")
                            (doseq [story-line (->> (str/split-lines icontent)
                                                    (filter #(re-find #"^- [a-z]" %))
                                                    (remove #(str/includes? % "**")))]
                              (when-let [bead-id (some-> (re-find #"([a-z]+-[a-z0-9-]+)" story-line) second)]
                                (it (str "Bead " bead-id " exists in tracker")
                                  (let [r (p/shell {:dir resolved :out :string :err :string :continue true}
                                                   "bd" "show" bead-id)]
                                    (zero? (:exit r))))))))
                        (spec-helper/fail! (str "iterations/" iter-name " missing ITERATION.md"))))))))))))))

;; ── Spawn Config ──

(describe "Spawn Config"
  (let [cron-output (try (:out (p/shell {:out :string :err :string :continue true} "openclaw" "cron" "list"))
                         (catch Exception _ nil))]
    (if cron-output
      (it "orchestrator cron job exists"
        (should-contain "projects" cron-output))
      (it "openclaw not in PATH (skip cron check)" true)))

  ;; Spawn config for active projects
  (let [content (slurp-safe registry)
        lines (str/split-lines (or content ""))
        data-lines (->> lines (filter #(str/includes? % "|")) (drop 2) (remove #(str/starts-with? (str/trim %) "|-")))]
    (doseq [line data-lines]
      (let [cols (->> (str/split line #"\|") (map str/trim) (remove str/blank?))
            [slug status _priority path] cols
            resolved (str/replace (or path "") "~" home)]
        (when (and slug (= status "active") (file? (str resolved "/PROJECT.md")))
          (let [pmd (slurp (str resolved "/PROJECT.md"))]
            (describe (str "Spawn Config: " slug)
              ;; MaxWorkers
              (let [mw (some-> (re-find #"MaxWorkers.*?(\d+)" pmd) second)]
                (if mw
                  (it (str "MaxWorkers is valid (" mw ")")
                    (pos? (parse-long mw)))
                  (it "MaxWorkers not set (default 1 applies)" true)))
              ;; Channel
              (let [ch (some-> (re-find #"Channel:\*\*\s*(.+)" pmd) second str/trim)]
                (it (str "Channel is set" (when ch (str " (" ch ")")))
                  (some? ch)))
              ;; iteration-complete mention
              (let [ic-line (some->> (str/split-lines pmd) (filter #(str/includes? % "iteration-complete")) first)]
                (if (and ic-line (re-find #"mention.*<@\d+>" ic-line))
                  (it "iteration-complete includes @mention" true)
                  (if (and ic-line (re-find #"(?i)on" ic-line))
                    (spec-helper/fail! "iteration-complete is on but missing @mention")
                    (it "iteration-complete not enabled (mention not required)" true)))))))))))
