(ns peatops.advisor-test
  (:require [clojure.test :refer [deftest is testing]]
            [peatops.advisor :as advisor]
            [peatops.store :as store]))

(def db (store/seed-db))

(deftest every-op-proposal-is-always-propose-effect
  (testing "the advisor NEVER drafts a direct-actuation :effect -- always :propose"
    (doseq [op [:log-extraction-record :schedule-extraction-operation :flag-environmental-concern :coordinate-shipment]]
      (let [p (advisor/infer db {:op op :site-id "peat-site-1" :patch {}})]
        (is (= :propose (:effect p)) (str op " must always propose, never actuate"))
        (is (= op (:op p)))
        (is (= "peat-site-1" (:site-id p)))
        (is (<= 0.0 (:confidence p) 1.0))
        (is (seq (:cites p)))))))

(deftest unrecognized-op-is-a-safe-noop
  (testing "an op outside the closed allowlist yields a safe zero-confidence :propose noop -- never a fabricated actuation"
    (let [p (advisor/infer db {:op :extract-material :site-id "peat-site-1" :patch {}})]
      (is (= :propose (:effect p)))
      (is (zero? (:confidence p))))))

(deftest environmental-concern-confidence-passes-through-patch
  (testing "a caller-supplied confidence on an environmental-concern proposal is honored (the governor, not the advisor, is what always escalates this op)"
    (let [p (advisor/infer db {:op :flag-environmental-concern :site-id "peat-site-1" :patch {:concern "bog runoff" :confidence 0.99}})]
      (is (= 0.99 (:confidence p))))))

(deftest out-of-scope-hook-drafts-a-detectably-poisoned-proposal
  (testing "the :out-of-scope? test hook drafts content the governor's scope-exclusion scan must catch -- proves the failure mode is real and testable end to end"
    (let [p (advisor/infer db {:op :schedule-extraction-operation :site-id "peat-site-1" :patch {} :out-of-scope? true})]
      (is (= :propose (:effect p)))
      (is (re-find #"(?i)vacuum harvester control" (str (:summary p) (:rationale p)))))))

(deftest mock-advisor-routes-through-infer
  (let [a (advisor/mock-advisor)
        p (advisor/-advise a db {:op :coordinate-shipment :site-id "peat-site-1" :patch {:carrier "rail-1"}})]
    (is (= :coordinate-shipment (:op p)))
    (is (= :propose (:effect p)))))

(deftest trace-carries-decision-grounded-fields
  (let [request {:op :log-extraction-record :site-id "peat-site-1"}
        proposal (advisor/infer db request)
        t (advisor/trace request proposal)]
    (is (= :log-extraction-record (:op t)))
    (is (= "peat-site-1" (:site-id t)))
    (is (= (:confidence proposal) (:confidence t)))))
