(ns init-reference-spec
  (:require [spec-helper :refer [describe context it should should-contain]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def project-root (str (System/getProperty "user.dir")))
(def skill-dir (str project-root "/projects"))
(def init-ref (str skill-dir "/references/init.md"))

(describe "Init Reference (references/init.md)"
  (it "init.md exists"
    (fs/exists? init-ref))

  (let [content (when (fs/exists? init-ref) (slurp init-ref))]
    (when content
      (doseq [section ["Install the Skill" "Verify beads" "Create PROJECTS_HOME"
                        "Orchestrator Cron" "Verification"]]
        (it (str "contains section: " section)
          (should-contain section content)))

      (it "references project-creation.md"
        (should-contain "project-creation.md" content))
      (it "references orchestrator.md"
        (should-contain "orchestrator.md" content))))

  (it "SKILL.md references init.md"
    (should-contain "init.md" (slurp (str skill-dir "/SKILL.md"))))

  (let [readme (str project-root "/README.md")]
    (it "README.md references init.md"
      (and (fs/exists? readme) (str/includes? (slurp readme) "init.md")))))
