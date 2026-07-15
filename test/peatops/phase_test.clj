(ns peatops.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:flag-environmental-concern` must NEVER be a member of
  any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [peatops.phase :as phase]))

(deftest environmental-concern-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in future entries, auto-commits an environmental-concern flag"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :flag-environmental-concern))
          (str "phase " n " must not auto-commit :flag-environmental-concern")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest extraction-logging-enabled-from-phase-1
  (is (contains? (:writes (get phase/phases 1)) :log-extraction-record))
  (is (not (contains? (:writes (get phase/phases 0)) :log-extraction-record))))

(deftest extraction-operation-and-shipment-enabled-from-phase-2
  (doseq [op [:schedule-extraction-operation :coordinate-shipment]]
    (is (contains? (:writes (get phase/phases 2)) op))
    (is (not (contains? (:writes (get phase/phases 1)) op)))))

(deftest environmental-concern-enabled-only-from-phase-3
  (is (contains? (:writes (get phase/phases 3)) :flag-environmental-concern))
  (is (not (contains? (:writes (get phase/phases 2)) :flag-environmental-concern))))

(deftest phase-3-auto-commits-three-of-four-ops
  (testing ":flag-environmental-concern is the only non-auto-eligible op at phase 3 -- always human sign-off"
    (is (= #{:log-extraction-record :schedule-extraction-operation :coordinate-shipment}
           (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :log-extraction-record} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :flag-environmental-concern} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :log-extraction-record} :commit))))
  (is (= :phase-disabled (:reason (phase/gate 0 {:op :log-extraction-record} :commit)))))

(deftest gate-auto-commits-a-clean-auto-eligible-write-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :log-extraction-record} :commit)))))

(deftest verdict->disposition-priority
  (is (= :hold (phase/verdict->disposition {:hard? true :escalate? false})))
  (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
  (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false}))))
