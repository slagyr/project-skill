(ns project-creation-reference-spec
  (:require [spec-helper :refer [describe context it should should-contain should-not]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def project-root (str (System/getProperty "user.dir")))
(def ref-file (str project-root "/projects/references/project-creation.md"))
(def skill-file (str project-root "/projects/SKILL.md"))
(def init-file (str project-root "/projects/references/init.md"))

(describe "project-creation.md Reference"
  (context "File exists"
    (it "project-creation.md exists"
      (fs/exists? ref-file)))

  (let [content (when (fs/exists? ref-file) (slurp ref-file))]
    (when content
      (context "Required sections"
        (doseq [section ["Gather Information" "Suggest Defaults" "Discord Channel"
                          "Scaffold" "Generate PROJECT.md" "AGENTS.md"
                          "Iteration 001" "Seed Stories" "Registry" "Review with the Human"]]
          (it (str "has " section " step")
            (should-contain section content))))

      (context "Key content"
        (it "references git init" (should-contain "git init" content))
        (it "references bd init" (should-contain "bd init" content))
        (it "references agents-template" (should-contain "agents-template" content))
        (it "has slug validation" (should-contain "slug" content))
        (it "no TODO placeholders" (should-contain "not a placeholder" content))
        (it "references PROJECT.md format" (should-contain "Status:" content))
        (it "references Notifications table" (should-contain "Notifications" content)))))

  (context "Old script removed"
    (it "projects-init script removed"
      (not (fs/exists? (str project-root "/projects/bin/projects-init"))))
    (it "test_projects_init.sh removed"
      (not (fs/exists? (str project-root "/tests/test_projects_init.sh")))))

  (context "SKILL.md updated"
    (let [skill (slurp skill-file)]
      (it "SKILL.md references project-creation.md"
        (should-contain "project-creation.md" skill))
      (it "SKILL.md no longer references projects-init"
        (should-not (str/includes? skill "projects-init")))))

  (context "init.md updated"
    (let [init (slurp init-file)]
      (it "init.md references project-creation.md"
        (should-contain "project-creation.md" init))
      (it "init.md no longer references projects-init"
        (should-not (str/includes? init "projects-init"))))))
