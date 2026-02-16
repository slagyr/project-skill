(ns braids.iteration-spec
  (:require [speclj.core :refer :all]
            [braids.iteration :as iter]))

(describe "braids.iteration"

  (describe "parse-iteration-edn"

    (it "parses a valid iteration.edn string"
      (let [edn-str (pr-str {:number "009" :status :active
                              :stories [{:id "proj-abc" :title "Do the thing"}
                                        {:id "proj-def" :title "Another thing"}]
                              :guardrails ["TDD"]
                              :notes ["Theme: testing"]})
            parsed (iter/parse-iteration-edn edn-str)]
        (should= "009" (:number parsed))
        (should= :active (:status parsed))
        (should= 2 (count (:stories parsed)))
        (should= ["TDD"] (:guardrails parsed))
        (should= ["Theme: testing"] (:notes parsed))))

    (it "applies defaults for missing guardrails and notes"
      (let [edn-str (pr-str {:number "001" :status :planning :stories []})
            parsed (iter/parse-iteration-edn edn-str)]
        (should= [] (:guardrails parsed))
        (should= [] (:notes parsed)))))

  (describe "validate-iteration"

    (it "returns empty errors for valid iteration"
      (should= [] (iter/validate-iteration {:number "001" :status :active :stories []})))

    (it "catches missing number"
      (should-contain "Missing :number"
        (iter/validate-iteration {:status :active :stories []})))

    (it "catches missing status"
      (should-contain "Missing :status"
        (iter/validate-iteration {:number "001" :stories []})))

    (it "catches invalid status"
      (should-contain "Invalid status: :bogus"
        (iter/validate-iteration {:number "001" :status :bogus :stories []})))

    (it "catches missing stories"
      (should-contain "Missing or invalid :stories"
        (iter/validate-iteration {:number "001" :status :active}))))

  (describe "iteration->edn-string"

    (it "round-trips through parse"
      (let [iter-map {:number "003" :status :active
                      :stories [{:id "abc" :title "Thing"}]
                      :guardrails ["TDD"] :notes ["note"]}
            edn-str (iter/iteration->edn-string iter-map)
            parsed (iter/parse-iteration-edn edn-str)]
        (should= "003" (:number parsed))
        (should= :active (:status parsed))
        (should= [{:id "abc" :title "Thing"}] (:stories parsed)))))

  (describe "migrate-iteration-md"

    (it "converts ITERATION.md content to EDN map"
      (let [md "# Iteration 009\n\nStatus: active\n\n## Stories\n- proj-abc: Do the thing\n- proj-def: Another thing\n\n## Guardrails\n- TDD\n\n## Notes\n- Theme: testing\n"
            result (iter/migrate-iteration-md md)]
        (should= "009" (:number result))
        (should= :active (:status result))
        (should= [{:id "proj-abc" :title "Do the thing"}
                  {:id "proj-def" :title "Another thing"}]
                 (:stories result))
        (should= ["TDD"] (:guardrails result))
        (should= ["Theme: testing"] (:notes result))))

    (it "handles ITERATION.md with no guardrails or notes"
      (let [md "# Iteration 001\n\nStatus: planning\n\n## Stories\n"
            result (iter/migrate-iteration-md md)]
        (should= "001" (:number result))
        (should= :planning (:status result))
        (should= [] (:stories result))
        (should-not (contains? result :guardrails))
        (should-not (contains? result :notes))))

    (it "handles bold status format"
      (let [md "# Iteration 003\n\n- **Status:** active\n\n## Stories\n- abc: Thing\n"
            result (iter/migrate-iteration-md md)]
        (should= :active (:status result)))))

  ;; Legacy parse functions (still needed for migration)
  (describe "parse-iteration-stories (legacy)"

    (it "extracts story ids and titles from ITERATION.md content"
      (let [content "# Iteration 009\n\nStatus: active\n\n## Stories\n- proj-abc: Do the thing\n- proj-def: Another thing\n\n## Guardrails\n- TDD"
            stories (iter/parse-iteration-stories content)]
        (should= [{:id "proj-abc" :title "Do the thing"}
                  {:id "proj-def" :title "Another thing"}]
                 stories)))

    (it "returns empty list when no stories section"
      (should= [] (iter/parse-iteration-stories "# Iteration 001\n\nStatus: active\n")))

    (it "handles stories with colons in titles"
      (let [content "## Stories\n- proj-abc: foo: bar baz\n"
            stories (iter/parse-iteration-stories content)]
        (should= [{:id "proj-abc" :title "foo: bar baz"}] stories))))

  (describe "parse-iteration-number (legacy)"

    (it "extracts iteration number from header"
      (should= "009" (iter/parse-iteration-number "# Iteration 009\n\nStatus: active")))

    (it "returns nil when no header"
      (should-be-nil (iter/parse-iteration-number "No header here"))))

  (describe "parse-iteration-status (legacy)"

    (it "extracts status"
      (should= "active" (iter/parse-iteration-status "# Iteration 009\n\nStatus: active")))

    (it "tolerates bold formatting"
      (should= "active" (iter/parse-iteration-status "- **Status:** active"))))

  (describe "annotate-stories"

    (it "annotates stories with bead status info"
      (let [stories [{:id "proj-abc" :title "Do thing"}
                     {:id "proj-def" :title "Other"}]
            beads [{"id" "proj-abc" "status" "open" "priority" 1 "dependencies" []}
                   {"id" "proj-def" "status" "closed" "priority" 1 "dependencies" []}
                   {"id" "proj-zzz" "status" "open" "priority" 2 "dependencies" []}]
            result (iter/annotate-stories stories beads)]
        (should= "open" (:status (first result)))
        (should= "closed" (:status (second result)))))

    (it "marks missing beads as unknown"
      (let [stories [{:id "proj-abc" :title "Do thing"}]
            result (iter/annotate-stories stories [])]
        (should= "unknown" (:status (first result))))))

  (describe "completion-stats"

    (it "calculates completion percentage"
      (let [stories [{:id "a" :status "closed"}
                     {:id "b" :status "open"}
                     {:id "c" :status "closed"}
                     {:id "d" :status "in_progress"}]]
        (should= {:total 4 :closed 2 :percent 50}
                 (iter/completion-stats stories))))

    (it "handles empty stories"
      (should= {:total 0 :closed 0 :percent 0}
               (iter/completion-stats []))))

  (describe "format-iteration"

    (it "formats iteration for human-readable output"
      (let [data {:number "009"
                  :status "active"
                  :stories [{:id "proj-abc" :title "Do thing" :status "open" :priority 1 :deps []}
                            {:id "proj-def" :title "Done" :status "closed" :priority 1 :deps []}]
                  :stats {:total 2 :closed 1 :percent 50}}
            output (iter/format-iteration data)]
        (should-contain "Iteration 009" output)
        (should-contain "active" output)
        (should-contain "50%" output)
        (should-contain "proj-abc" output)
        (should-contain "proj-def" output)
        (should-contain "open" output)
        (should-contain "closed" output)))

    (it "shows dependencies"
      (let [data {:number "001"
                  :status "active"
                  :stories [{:id "proj-abc" :title "Thing" :status "open" :priority 1
                             :deps ["proj-def"]}]
                  :stats {:total 1 :closed 0 :percent 0}}
            output (iter/format-iteration data)]
        (should-contain "proj-def" output)))

    (it "formats as JSON when requested"
      (let [data {:number "001"
                  :status "active"
                  :stories [{:id "a" :title "T" :status "open" :priority 1 :deps []}]
                  :stats {:total 1 :closed 0 :percent 0}}
            output (iter/format-iteration-json data)]
        (should-contain "\"number\"" output)
        (should-contain "\"stories\"" output)
        (should-contain "\"percent\"" output)))))
