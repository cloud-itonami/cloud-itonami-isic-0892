(ns peatops.governor-test
  "Pure unit tests of `peatops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-
  test`'s full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [peatops.governor :as gov]
            [peatops.store :as store]))

(def site-1 {:site-id "peat-site-1" :name "Northern Milled-Peat Bog" :method :milled-peat :registered? true :verified? true})
(def site-3 {:site-id "peat-site-3" :name "Southern Bog Extraction Site" :method :sod-peat :registered? true :verified? false})

(defn- clean-proposal [op site-id]
  {:op op :site-id site-id :summary "s" :rationale "routine back-office coordination"
   :cites [site-id] :effect :propose :value {} :confidence 0.85})

(deftest site-unregistered-is-hard
  (testing "no site record at all -> HARD hold"
    (let [s (store/mem-store {"peat-site-1" site-1})
          verdict (gov/check {} nil (clean-proposal :log-extraction-record "unknown-site") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:site-unverified} (map :rule (:violations verdict)))))))

(deftest site-unverified-is-hard
  (testing "site registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"peat-site-3" site-3})
          verdict (gov/check {} nil (clean-proposal :log-extraction-record "peat-site-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:site-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"peat-site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :coordinate-shipment "peat-site-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"peat-site-1" site-1})
          verdict (gov/check {} nil (clean-proposal :extract-material "peat-site-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest vacuum-harvester-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches vacuum-harvester-control/milling-machine-control scope is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"peat-site-1" site-1})
          poisoned (assoc (clean-proposal :schedule-extraction-operation "peat-site-1")
                          :rationale "adjusted the vacuum harvester control settings and milling machine control pattern"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest drainage-ditch-control-content-is-hard
  (testing "a proposal touching bog-drainage-ditch control is HARD-blocked, same as milled-peat equipment control"
    (let [s (store/mem-store {"peat-site-1" site-1})
          poisoned (assoc (clean-proposal :schedule-extraction-operation "peat-site-1")
                          :rationale "revised the drainage ditch control setpoints for section 4")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest environmental-permit-authority-content-is-hard
  (testing "a proposal touching environmental-permit-issuing-authority decisions (permit issuance / license suspension / compliance enforcement) is HARD-blocked"
    (let [s (store/mem-store {"peat-site-1" site-1})
          poisoned (assoc (clean-proposal :coordinate-shipment "peat-site-1")
                          :summary "recommend license suspension pending review")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-environmental-flag-is-not-scope-excluded
  (testing "flagging observed bog-drainage/wetland-impact/fire-risk issues as an ENVIRONMENTAL CONCERN (not a control decision) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"peat-site-1" site-1})
          concern (assoc (clean-proposal :flag-environmental-concern "peat-site-1")
                         :value {:concern "elevated runoff near drainage ditch, possible wetland impact"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (bog-drainage/wetland-impact) is exactly what this op exists to surface"))))

(deftest environmental-flag-always-escalates
  (testing ":flag-environmental-concern ALWAYS escalates, even at maximum confidence"
    (let [s (store/mem-store {"peat-site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-environmental-concern "peat-site-1") :confidence 1.0) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (false? (:ok? verdict))))))

(deftest low-confidence-escalates
  (testing "confidence below the floor escalates a proposal that is otherwise clean"
    (let [s (store/mem-store {"peat-site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :log-extraction-record "peat-site-1") :confidence 0.4) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict)))
      (is (false? (:ok? verdict))))))

(deftest hard-violation-wins-over-escalate
  (testing "a HARD violation on an always-escalate op still reports hard?, not merely escalate?"
    (let [s (store/mem-store {})
          verdict (gov/check {} nil (clean-proposal :flag-environmental-concern "unknown-site") s)]
      (is (true? (:hard? verdict)))
      (is (false? (:escalate? verdict)) "hard? wins -- escalate? is false when hard? is true"))))

(deftest happy-path-every-op-is-clean
  (testing "each of the four allowlisted ops, clean input, registered+verified site -> ok"
    (let [s (store/mem-store {"peat-site-1" site-1})]
      (doseq [op [:log-extraction-record :schedule-extraction-operation :coordinate-shipment]]
        (let [verdict (gov/check {} nil (clean-proposal op "peat-site-1") s)]
          (is (false? (:hard? verdict)) (str op " should have no hard violations"))
          (is (false? (:escalate? verdict)) (str op " should not escalate when clean and confident"))
          (is (true? (:ok? verdict)) (str op " should be ok")))))))
