(ns soukai.facts
  "Japan-only 株主総会 (shareholders' general meeting) legal-basis catalog —
  the G2-style citation table `soukai.governor` checks every assess proposal
  against, mirroring `holdco.facts`'s (cloud-itonami-isic-6420) honesty
  discipline: every entry cites a real statute article number AND an e-Gov
  法令検索 law-id, verified to exist at ADR-2607121700 write-time
  (会社法 = `417AC0000000086`, 会社法施行規則 = `418M60000010012`), never a
  fabricated citation.

  **R0 scope, stated honestly, not silently assumed**:
    - Japan only. No other jurisdiction's corporate-meeting law is modeled
      or claimed.
    - 法定原則 (statutory defaults) ONLY. A real company's 定款
      (articles of incorporation) can shorten/lengthen/tighten many of
      these figures within the bounds the statute allows (e.g. 299条1項's
      notice period can be shortened by AOI in some cases; 309条2項's
      quorum can be reduced to 1/3 by AOI). This catalog does NOT model
      any individual company's AOI customization — operators who need
      AOI-aware numbers must layer that on top; treat every figure below
      as 'the law says at minimum/by default', never as 'this specific
      company's actual rule'.
    - `resolution-requirements`'s abstention convention (excluded from the
      approval threshold's denominator) is a modeling CHOICE among two
      legitimate real-world readings, flagged explicitly at
      `soukai.tally` — not asserted as the only correct interpretation of
      会社法309条.

  Coverage is reported the same way `holdco.facts/coverage` reports
  jurisdiction coverage: what's here is here, what's not modeled is not
  silently assumed to be covered."
  (:require [clojure.string :as str]))

;; ───────────────────────── 会社法第299条第1項: 招集通知の期間 ─────────────────────────

(def notice-period-catalog
  "meeting's :notice-scenario -> minimum notice-period-days before the
  meeting date, per 会社法第299条第1項. `soukai.governor/notice-period-gate`
  is the HARD check that enforces this against a committed convocation
  draft's proposed :notice-date."
  {:public-or-voting-enabled
   {:name "公開会社、又は書面/電子投票を採用する会社"
    :legal-basis "会社法(平成17年法律第86号)第299条第1項"
    :notice-period-days 14
    :provenance "https://laws.e-gov.go.jp/law/417AC0000000086"}
   :non-public-with-board
   {:name "非公開会社・取締役会設置会社"
    :legal-basis "会社法第299条第1項"
    :notice-period-days 7
    :provenance "https://laws.e-gov.go.jp/law/417AC0000000086"}
   :non-public-without-board
   {:name "非公開会社・取締役会非設置会社"
    :legal-basis "会社法第299条第1項"
    :notice-period-days 7
    :note "定款でこれを下回る期間を定めた場合はその期間による。本R0は法定原則の7日のみモデル化し、個別の定款内容までは推測しない。"
    :provenance "https://laws.e-gov.go.jp/law/417AC0000000086"}})

;; ───────────────────────── 会社法第309条: 決議要件 ─────────────────────────

(def resolution-requirements
  "agenda's :resolution-type -> quorum/threshold fractions, per 会社法第309条.
  `soukai.tally/outcome-of` is the pure function that applies this table's
  numbers; `soukai.governor/resolution-mismatch` is the HARD check that
  forces the secretary-LLM's proposed :outcome to equal that pure
  function's output, verbatim, every time."
  {:ordinary-resolution
   {:legal-basis "会社法第309条第1項"
    :quorum-num 1 :quorum-den 2 :threshold-num 1 :threshold-den 2
    :note "定款で別段の定め可(本R0は法定原則のみ)"
    :provenance "https://laws.e-gov.go.jp/law/417AC0000000086"}
   :special-resolution
   {:legal-basis "会社法第309条第2項"
    :quorum-num 1 :quorum-den 2 :threshold-num 2 :threshold-den 3
    :note "定款により定足数は3分の1まで軽減可、決議要件は引き上げのみ可(本R0は法定原則のみ)"
    :provenance "https://laws.e-gov.go.jp/law/417AC0000000086"}
   :special-resolution-2
   {:legal-basis "会社法第309条第3項・第4項"
    :headcount-threshold-num 1 :headcount-threshold-den 2
    :rights-threshold-num 2 :rights-threshold-den 3
    :note "定足数要件なし。表決数は総株主の頭数の半数以上かつ議決権の3分の2以上(3項)。第4項(109条2項関連の定款変更)は議決権4分の3以上だが本R0では3項の比率のみ既定値として扱い、4項該当ケースはoperatorが個別に閾値を差し替える前提とする。"
    :provenance "https://laws.e-gov.go.jp/law/417AC0000000086"}})

;; ───────────────────────── 会社法第325条の3: 電子提供制度 ─────────────────────────

(def electronic-provision
  {:legal-basis "会社法第325条の3"
   :minimum-days-before-meeting 21
   :note "総会の日の3週間前の日又は招集通知を発した日のいずれか早い日から、総会の日後3箇月を経過する日まで継続して電子提供措置をとる義務(令和4年9月1日施行。振替株式発行会社は令和5年3月1日以降開催分から義務化)。本R0は開始側の21日のみを構造チェックし、終了側の継続義務は対象外。"
   :provenance "https://laws.e-gov.go.jp/law/417AC0000000086"})

;; ───────────────────────── 会社法施行規則第72条: 議事録記載事項 ─────────────────────────

(def minutes-required-fields
  {:legal-basis "会社法施行規則(平成18年法務省令第12号)第72条"
   :fields #{:held-at :held-on :proceedings-summary :results :attending-officers :minutes-preparer}
   :provenance "https://laws.e-gov.go.jp/law/418M60000010012"})

;; ───────────────────────── portable date arithmetic ─────────────────────────
;;
;; The notice-period/electronic-provision gates need exact day-count math
;; over ISO-8601 date(-time) strings — a legally-binding computation that
;; must never round or drift. Rather than pull in java.time (JVM-only) or
;; goog.date (cljs-only), this uses Howard Hinnant's `days_from_civil` /
;; `civil_from_days` — pure proleptic-Gregorian integer arithmetic — so the
;; SAME code runs bit-identically on JVM Clojure and ClojureScript, matching
;; this repo's .cljc portability convention (`CLAUDE.md`'s runtime-priority
;; rule: prefer portable .cljc over host-specific date libraries).

(defn- parse-int [s] #?(:clj (Integer/parseInt ^String s) :cljs (js/parseInt s 10)))

(defn- days-from-civil
  "Proleptic Gregorian (y m d) -> integer day count relative to the
  1970-01-01 epoch. http://howardhinnant.github.io/date_algorithms.html"
  [y m d]
  (let [y'  (if (<= m 2) (dec y) y)
        era (quot (if (>= y' 0) y' (- y' 399)) 400)
        yoe (- y' (* era 400))
        mp  (mod (+ m 9) 12)
        doy (+ (quot (+ (* 153 mp) 2) 5) (dec d))
        doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
    (+ (* era 146097) doe -719468)))

(defn- civil-from-days
  "Inverse of `days-from-civil`: an epoch day count -> [y m d]."
  [z]
  (let [z    (+ z 719468)
        era  (quot (if (>= z 0) z (- z 146096)) 146097)
        doe  (- z (* era 146097))
        yoe  (quot (- doe (quot doe 1460) (- (quot doe 36524)) (quot doe 146096)) 365)
        y    (+ yoe (* era 400))
        doy  (- doe (+ (* 365 yoe) (quot yoe 4) (- (quot yoe 100))))
        mp   (quot (+ (* 5 doy) 2) 153)
        d    (inc (- doy (quot (+ (* 153 mp) 2) 5)))
        m    (if (< mp 10) (+ mp 3) (- mp 9))]
    [(if (<= m 2) (inc y) y) m d]))

(defn- pad [n width] (let [s (str n) k (max 0 (- width (count s)))] (str (apply str (repeat k "0")) s)))

(defn date->day
  "First 10 chars (\"YYYY-MM-DD\") of an ISO-8601 date or date-time string ->
  epoch day count. Both a bare date (\"2026-06-30\") and a Z-suffixed
  date-time (\"2026-08-15T10:00:00Z\") parse identically since only the
  date prefix is used."
  [iso]
  (let [[y m d] (map parse-int (str/split (subs (str iso) 0 10) #"-"))]
    (days-from-civil y m d)))

(defn day->date-str [day]
  (let [[y m d] (civil-from-days day)]
    (str (pad y 4) "-" (pad m 2) "-" (pad d 2))))

(defn days-between
  "Whole days from `iso-a` to `iso-b` (b - a). Negative if b is earlier."
  [iso-a iso-b]
  (- (date->day iso-b) (date->day iso-a)))

(defn minus-days
  "`iso` minus `n` days, as a bare \"YYYY-MM-DD\" string."
  [iso n]
  (day->date-str (- (date->day iso) n)))
