(ns integration-smoke-spec
  (:require [spec-helper :refer [describe context it should should= should-contain should-match]]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def home (System/getProperty "user.home"))
(def projects-home (or (System/getenv "PROJECTS_HOME") (str home "/Projects")))
(def registry (str projects-home "/registry.md"))

(defn slurp-safe [path] (when (fs/exists? path) (slurp path)))

(defn extract-bead-ids [iter-md-content]
  (->> (str/split-lines iter-md-content)
       (filter #(re-find #"^- [a-z]" %))
       (remove #(str/includes? % "**"))
       (filter #(str/includes? % ":"))
       (map #(second (re-find #"^- ([^:]+):" %)))
       (map str/trim)
       (remove str/blank?)))

(defn get-bead-status [project-dir bead-id]
  (try
    (let [r (p/shell {:dir project-dir :out :string :err :string :continue true} "bd" "show" bead-id)]
      (if (zero? (:exit r))
        (or (some-> (re-find #"\b(OPEN|CLOSED|BLOCKED|IN_PROGRESS)\b" (:out r)) first) "UNKNOWN")
        "UNKNOWN"))
    (catch Exception _ "UNKNOWN")))

(defn bead-suffix [bead-id] (last (str/split bead-id #"-")))

(defn find-deliverable [iter-dir bead-id]
  (let [suffix (bead-suffix bead-id)
        files (fs/glob iter-dir "*.md")
        names (map #(str (fs/file-name %)) files)
        filtered (remove #(#{"ITERATION.md" "RETRO.md"} %) names)]
    (some (fn [n]
            (or (str/starts-with? n (str suffix "-"))
                (= n (str bead-id ".md"))))
          filtered)))

(defn git-has-commit? [project-dir bead-id]
  (try
    (let [r (p/shell {:dir project-dir :out :string :err :string :continue true}
                     "git" "log" "--oneline" "--all" (str "--grep=" bead-id))]
      (and (zero? (:exit r)) (not (str/blank? (:out r)))))
    (catch Exception _ false)))

;; ── Preflight ──

(when-not (fs/exists? registry)
  (spec-helper/fail! "Registry not found")
  (spec-helper/run-and-exit))

;; ── Parse registry ──

(let [reg-content (slurp registry)
      lines (str/split-lines reg-content)
      data-lines (->> lines (filter #(str/includes? % "|")) (drop 2) (remove #(str/starts-with? (str/trim %) "|-")))]

  (doseq [line data-lines]
    (let [cols (->> (str/split line #"\|") (map str/trim) (remove str/blank?))
          [slug status _priority path] cols
          resolved (str/replace (or path "") "~" home)]
      (when (and slug (not= slug "Slug") (not (str/starts-with? slug "-"))
                 (fs/directory? resolved) (fs/exists? (str resolved "/PROJECT.md")))

        (describe (str "Project: " slug)

          ;; Git state
          (context "Git State"
            (when (fs/directory? (str resolved "/.git"))
              (let [unpushed (try
                               (let [r (p/shell {:dir resolved :out :string :err :string :continue true}
                                                "git" "log" "--oneline" "@{u}..HEAD")]
                                 (if (zero? (:exit r))
                                   (count (remove str/blank? (str/split-lines (:out r))))
                                   0))
                               (catch Exception _ 0))]
                (it (str "unpushed commits within tolerance (" unpushed ")")
                  (<= unpushed 5)))
              (let [branch (try
                             (str/trim (:out (p/shell {:dir resolved :out :string :err :string :continue true}
                                                      "git" "branch" "--show-current")))
                             (catch Exception _ ""))]
                (it (str "on branch '" branch "'")
                  (not (str/blank? branch))))))

          ;; Completed iterations
          (when (fs/directory? (str resolved "/iterations"))
            (doseq [iter-dir (sort (fs/list-dir (str resolved "/iterations")))]
              (let [iter-name (str (fs/file-name iter-dir))
                    iter-md (str iter-dir "/ITERATION.md")]
                (when (and (fs/directory? iter-dir) (re-matches #"\d{3}" iter-name) (fs/exists? iter-md))
                  (let [icontent (slurp iter-md)
                        iter-status (some-> (re-find #"(?i)Status:\*\*\s*(.*)|Status:\s*(.*)" icontent)
                                            rest (->> (remove nil?) first str/trim))]

                    ;; Completed iteration checks
                    (when (= iter-status "complete")
                      (context (str "Completed Iteration " iter-name)
                        (it "RETRO.md exists"
                          (fs/exists? (str iter-dir "/RETRO.md")))

                        (let [bead-ids (extract-bead-ids icontent)]
                          (doseq [bid bead-ids]
                            (it (str "deliverable exists for " bid)
                              (find-deliverable iter-dir bid))
                            (let [bd-status (get-bead-status resolved bid)]
                              (if (= bd-status "UNKNOWN")
                                (it (str "bead " bid " not in bd (archived — OK)") true)
                                (it (str "bead " bid " is closed in bd")
                                  (= bd-status "CLOSED"))))
                            (it (str "git commit found for " bid)
                              (git-has-commit? resolved bid))))))

                    ;; Active iteration checks
                    (when (= iter-status "active")
                      (context (str "Active Iteration " iter-name)
                        (let [bead-ids (extract-bead-ids icontent)
                              statuses (map (fn [bid] [bid (get-bead-status resolved bid)]) bead-ids)
                              closed-count (count (filter #(= "CLOSED" (second %)) statuses))
                              total-count (count bead-ids)]

                          (doseq [[bid bd-status] statuses]
                            (when (= bd-status "CLOSED")
                              (it (str "closed bead " bid " has deliverable")
                                (find-deliverable iter-dir bid))
                              (it (str "closed bead " bid " has git commit")
                                (git-has-commit? resolved bid)))
                            (it (str "bead " bid " exists in bd (" bd-status ")")
                              (not= bd-status "UNKNOWN")))

                          (it (str "active iteration " iter-name ": " closed-count "/" total-count " beads closed")
                            true)

                          (when (and (pos? total-count) (= closed-count total-count))
                            (spec-helper/fail! "All beads closed but iteration still active"))))))))))

          ;; At most one active iteration
          (let [active-count (->> (fs/glob resolved "iterations/*/ITERATION.md")
                                  (map #(slurp (str %)))
                                  (filter #(re-find #"Status:.*active" %))
                                  count)]
            (it (str "at most one active iteration (" active-count ")")
              (<= active-count 1)))

          ;; Orphaned deliverables
          (when (fs/directory? (str resolved "/iterations"))
            (doseq [iter-dir (sort (fs/list-dir (str resolved "/iterations")))]
              (let [iter-name (str (fs/file-name iter-dir))
                    iter-md (str iter-dir "/ITERATION.md")]
                (when (and (fs/directory? iter-dir) (re-matches #"\d{3}" iter-name) (fs/exists? iter-md))
                  (let [icontent (slurp iter-md)
                        bead-ids (extract-bead-ids icontent)
                        suffixes (set (map bead-suffix bead-ids))
                        full-ids (set bead-ids)]
                    (doseq [f (fs/glob iter-dir "*.md")]
                      (let [fname (str (fs/file-name f))]
                        (when-not (#{"ITERATION.md" "RETRO.md"} fname)
                          (let [prefix (first (str/split fname #"-"))
                                no-ext (str/replace fname #"\.md$" "")]
                            (it (str "deliverable " fname " matches a bead")
                              (or (contains? suffixes prefix)
                                  (contains? full-ids no-ext))))))))))))))))

  ;; ── Cross-project checks ──

  (describe "Cross-Project Checks"
    (context "STATUS.md"
      (let [status-file (str projects-home "/STATUS.md")]
        (if (fs/exists? status-file)
          (do
            (it "STATUS.md exists" true)
            (let [mtime (.toMillis (fs/last-modified-time status-file))
                  now (System/currentTimeMillis)
                  age-s (/ (- now mtime) 1000)]
              (it (str "STATUS.md freshness (" (long (/ age-s 3600)) "h ago)")
                (< age-s 86400))))
          (spec-helper/fail! "STATUS.md not found"))))

    (context "Orchestrator State"
      (let [state-file (str projects-home "/.orchestrator-state.json")]
        (if (fs/exists? state-file)
          (do
            (it "orchestrator state file exists" true)
            (let [parsed (try (json/parse-string (slurp state-file)) (catch Exception _ nil))]
              (if parsed
                (do
                  (it "state is valid JSON" true)
                  (it "state has lastRunAt" (contains? parsed "lastRunAt"))
                  (it "state has idleSince" (contains? parsed "idleSince"))
                  (it "state has idleReason" (contains? parsed "idleReason"))
                  (let [reason (get parsed "idleReason")]
                    (it (str "idleReason is valid (" reason ")")
                      (or (nil? reason)
                          (contains? #{"no-active-iterations" "no-ready-beads" "all-at-capacity"} reason)))))
                (spec-helper/fail! "state is not valid JSON"))))
          (spec-helper/fail! "orchestrator state file not found"))))))
