(ns braids.migration-spec
  (:require [speclj.core :refer :all]
            [braids.migration :as mig]))

(describe "braids.migration"

  (describe "parse-registry-md"

    (it "parses a markdown registry table"
      (let [md (str "# Projects\n\n"
                    "| Slug | Status | Priority | Path |\n"
                    "|------|--------|----------|------|\n"
                    "| my-proj | active | normal | ~/Projects/my-proj |\n"
                    "| other | paused | high | ~/Projects/other |\n")
            result (mig/parse-registry-md md)]
        (should= 2 (count (:projects result)))
        (should= "my-proj" (:slug (first (:projects result))))
        (should= :active (:status (first (:projects result))))
        (should= :high (:priority (second (:projects result))))))

    (it "handles empty table"
      (let [md (str "# Projects\n\n"
                    "| Slug | Status | Priority | Path |\n"
                    "|------|--------|----------|------|\n")
            result (mig/parse-registry-md md)]
        (should= [] (:projects result)))))

  (describe "parse-project-md"

    (it "extracts config fields from PROJECT.md"
      (let [md (str "# My Project\n\n"
                    "- **Status:** active\n"
                    "- **Priority:** high\n"
                    "- **Autonomy:** full\n"
                    "- **Checkin:** daily\n"
                    "- **Channel:** 123456\n"
                    "- **MaxWorkers:** 3\n"
                    "- **WorkerTimeout:** 7200\n\n"
                    "## Goal\nBuild something great\n")
            result (mig/parse-project-md md)]
        (should= "My Project" (:name result))
        (should= :active (:status result))
        (should= :high (:priority result))
        (should= :full (:autonomy result))
        (should= :daily (:checkin result))
        (should= "123456" (:channel result))
        (should= 3 (:max-workers result))
        (should= 7200 (:worker-timeout result))))

    (it "applies defaults for missing fields"
      (let [md (str "# Minimal\n\n"
                    "- **Status:** active\n"
                    "- **Priority:** normal\n"
                    "- **Autonomy:** full\n")
            result (mig/parse-project-md md)]
        (should= "Minimal" (:name result))
        (should= 1 (:max-workers result))
        (should= 3600 (:worker-timeout result))
        (should= :on-demand (:checkin result))))

    (it "parses notifications table"
      (let [md (str "# Test\n\n"
                    "- **Status:** active\n"
                    "- **Priority:** normal\n"
                    "- **Autonomy:** full\n\n"
                    "## Notifications\n\n"
                    "| Event | Notify |\n"
                    "|-------|--------|\n"
                    "| iteration-start | on |\n"
                    "| bead-complete | off |\n"
                    "| blocker | on (mention <@123>) |\n")
            result (mig/parse-project-md md)]
        (should= true (get-in result [:notifications :iteration-start]))
        (should= false (get-in result [:notifications :bead-complete]))
        (should= true (get-in result [:notifications :blocker]))
        (should= "<@123>" (get-in result [:notification-mentions :blocker]))))))
