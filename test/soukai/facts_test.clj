(ns soukai.facts-test
  "Sanity checks on the R0 legal-basis catalog: the documented figures are
  actually what's in the maps, and every entry cites a non-blank
  legal-basis + e-Gov provenance URL (never a fabricated citation)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [soukai.facts :as facts]))

(deftest notice-period-catalog-sanity
  (testing "documented day counts (会社法第299条第1項)"
    (is (= 14 (get-in facts/notice-period-catalog [:public-or-voting-enabled :notice-period-days])))
    (is (= 7 (get-in facts/notice-period-catalog [:non-public-with-board :notice-period-days])))
    (is (= 7 (get-in facts/notice-period-catalog [:non-public-without-board :notice-period-days]))))
  (testing "every entry cites a non-blank legal-basis + e-Gov provenance"
    (doseq [[k m] facts/notice-period-catalog]
      (is (not (str/blank? (:legal-basis m))) (str k))
      (is (not (str/blank? (:provenance m))) (str k))
      (is (str/starts-with? (:provenance m) "https://laws.e-gov.go.jp/") (str k)))))

(deftest resolution-requirements-sanity
  (testing "ordinary-resolution: 1/2 quorum, 1/2 threshold (会社法第309条第1項)"
    (let [r (:ordinary-resolution facts/resolution-requirements)]
      (is (= 1 (:quorum-num r))) (is (= 2 (:quorum-den r)))
      (is (= 1 (:threshold-num r))) (is (= 2 (:threshold-den r)))))
  (testing "special-resolution: 1/2 quorum, 2/3 threshold (会社法第309条第2項)"
    (let [r (:special-resolution facts/resolution-requirements)]
      (is (= 1 (:quorum-num r))) (is (= 2 (:quorum-den r)))
      (is (= 2 (:threshold-num r))) (is (= 3 (:threshold-den r)))))
  (testing "special-resolution-2: no quorum key at all, 1/2 headcount + 2/3 rights (会社法第309条第3項/第4項)"
    (let [r (:special-resolution-2 facts/resolution-requirements)]
      (is (nil? (:quorum-num r)))
      (is (= 1 (:headcount-threshold-num r))) (is (= 2 (:headcount-threshold-den r)))
      (is (= 2 (:rights-threshold-num r))) (is (= 3 (:rights-threshold-den r)))))
  (testing "every entry cites a non-blank legal-basis + e-Gov provenance"
    (doseq [[k m] facts/resolution-requirements]
      (is (not (str/blank? (:legal-basis m))) (str k))
      (is (not (str/blank? (:provenance m))) (str k))
      (is (str/starts-with? (:provenance m) "https://laws.e-gov.go.jp/") (str k)))))

(deftest electronic-provision-sanity
  (is (= 21 (:minimum-days-before-meeting facts/electronic-provision)))
  (is (not (str/blank? (:legal-basis facts/electronic-provision))))
  (is (str/starts-with? (:provenance facts/electronic-provision) "https://laws.e-gov.go.jp/")))

(deftest minutes-required-fields-sanity
  (is (= #{:held-at :held-on :proceedings-summary :results :attending-officers :minutes-preparer}
         (:fields facts/minutes-required-fields)))
  (is (not (str/blank? (:legal-basis facts/minutes-required-fields))))
  (is (str/starts-with? (:provenance facts/minutes-required-fields) "https://laws.e-gov.go.jp/")))

(deftest date-arithmetic-sanity
  (testing "portable Hinnant days-from-civil / civil-from-days round-trips and matches calendar math by hand"
    (is (= 46 (facts/days-between "2026-06-30" "2026-08-15")) "30 (June) + 16 (to Aug 15) = 46")
    (is (= "2026-08-08" (facts/minus-days "2026-08-15T10:00:00Z" 7)))
    (is (= "2026-08-15" (facts/day->date-str (facts/date->day "2026-08-15"))))))

(deftest date-arithmetic-year-boundary
  (testing "day counting crosses a year boundary correctly"
    (is (= 1 (facts/days-between "2025-12-31" "2026-01-01")))
    (is (= "2026-01-01" (facts/day->date-str (facts/date->day "2026-01-01"))))))
