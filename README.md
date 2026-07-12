# soukai

総会 — a **shareholders'-general-meeting (株主総会) governance control plane**:
a secretary-LLM ⊣ ResolutionGovernor StateGraph that drafts convocation
notices, explains resolution outcomes, and drafts legal minutes, but never
sends a notice, records a resolution's outcome, or finalizes a minutes
record itself. The actor is **propose → draft only**: a draft commits as
data (a *casual commit* — phase-gated auto-approval is fine, it's just
proposed/explanatory content sitting there for review); actually **sending**
the convocation notice and **finalizing** the minutes are **always a human
call**, regardless of phase — and a resolution's recorded outcome is always
the verbatim output of a pure deterministic function, never the LLM's own
judgment.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph) StateGraph runtime —
the same pattern as [`koyomi`](../koyomi) (schedule-LLM ⊣ ComplianceGovernor),
[`kekkai`](../kekkai) (coord-LLM ⊣ TailnetGovernor), and
[`tayori`](../tayori) (reply-LLM ⊣ ComplianceGovernor). Here it is
**secretary-LLM ⊣ ResolutionGovernor**.

> Charter: **(G1)** propose → draft only, no direct actuation — the actor
> writes proposed/explanatory content, a human turns it into an outbound
> notice send or a legally-final minutes record; **(G2)** sending the
> convocation notice and finalizing the minutes are **always a human call**
> (high-stakes), independent of rollout phase; **(G3)** kotoba-native —
> meeting/snapshot/agenda/vote facts are durable EAVT ground facts, drafts
> are transient until committed; **(G4)** a resolution's :outcome is ALWAYS
> `soukai.tally/outcome-of`'s verbatim output — the secretary-LLM never
> invents a different possible/passed/failed judgment, and
> `soukai.governor`'s `resolution-mismatch` HARD check makes this
> structurally un-overridable, even by a human approval (a mismatched
> proposal never reaches `:request-approval` at all — it holds directly).

## The core contract

```
meeting/snapshot/agenda/vote facts
        │  ingest = durable ground facts (observe; always on)
        ▼
   ┌──────────────┐  proposal: draft/    ┌──────────────────────┐
   │ secretary-LLM │  send/finalize       │ ResolutionGovernor    │ (independent system)
   │ (sealed)      │ ───────────────────▶ │  no-actuation ·        │
   └──────────────┘  + cited facts        │  notice-period ·       │
                                          │  electronic-provision · │
                                          │  resolution-mismatch ·  │
                                          │  minutes-fields ·       │
                                          │  tenant-isolation       │
                                          └──────────┬─────────────┘
                            commit ◀──────────────────┼─────────────▶ hold (notice too
                     (draft: casual              escalate         late / URL missing /
                      commit, auto ok                 │            outcome mismatch /
                      at phase≥2;                     ▼            fields missing /
                      send/finalize:              人間 承認        tenant mismatch;
                      ALWAYS here) ─────────▶ (send/finalizeは    un-overridable)
                                               phase に関わらず
                                               常に人間)
```

**The actor never sends/finalizes/records anything the ResolutionGovernor
would reject, and secretary-LLM never actuates directly or rewrites the
tally.** HARD invariants force **hold** (a human cannot approve past a
proposal for a nonexistent meeting/agenda, a claimed-already-actuated
proposal, a notice sent short of 会社法299条1項's minimum, a missing
325条の3 electronic-provision URL, a resolution :outcome that disagrees with
`soukai.tally`'s deterministic math, incomplete 施行規則72条 minutes fields,
or a tenant mismatch); a clean send/finalize still routes to a human. A
close-margin resolution outcome (within 5 percentage points of the exact
approval threshold) is a SOFT escalate, not a hold — the math is never in
question, but a human should still take a look at a close real-world call.

## Run

```bash
clojure -M:dev:run     # drive: convocation → resolution → minutes through the actor
clojure -M:dev:test    # the propose-only contract + tally math + store parity + CACAO crypto
clojure -M:lint        # clj-kondo (errors fail)
```

Demo: register a meeting's ground facts (observe → facts) → draft a clean
convocation notice (phase 3 → clean → auto-commits, no interrupt) → send it
(**always** human sign-off, even though clean) → a second meeting whose
already-committed convocation draft has a too-late notice date (**HARD
HOLD**, un-overridable) → finalize an ordinary-resolution's outcome (clean
majority → `:approved`, `soukai.tally`'s verbatim output) → finalize a
special-resolution whose attendance falls under quorum (`:no-quorum` — a
CLEAN commit, proving the math is honestly reported, not a violation) →
finalize a special-resolution-2 (頭数+議決権 both measured against the total
shareholder universe) → draft then finalize the legal minutes (**always**
human sign-off) → prints the meeting-governance audit ledger → swaps to
`DatomicStore` with identical results.

## Layout

| File | Role |
|---|---|
| `src/soukai/model.cljc` | the `draft` shape (secretary-LLM's proposal wrapper; see its docstring for how it adapts koyomi.model/draft's shape to soukai's three draft kinds) |
| `src/soukai/facts.cljc` | Japan-only R0 legal-basis catalog (会社法299条/309条/325条の3、施行規則72条) + portable (no java.time/goog.date) day-count arithmetic |
| `src/soukai/tally.cljc` | **pure deterministic vote tally** — `outcome-of`, the single source of truth for 可決/否決/定足数未達, zero LLM/governor/store dependency |
| `src/soukai/store.cljc` | **Store** protocol — `MemStore` ‖ `DatomicStore` (`langchain.db`, swappable to Datomic Local / kotoba-server) + append-only **meeting-governance audit ledger** |
| `src/soukai/secretaryllm.cljc` | **secretary-LLM Advisor** — `mock-advisor` ‖ `llm-advisor` (`langchain.model`); convocation/resolution/minutes proposals |
| `src/soukai/governor.cljc` | **ResolutionGovernor** — no-actuation · notice-period · electronic-provision · resolution-mismatch · minutes-legal-fields · tenant-isolation · close-margin (soft) · high-stakes |
| `src/soukai/phase.cljc` | **Phase 0→3** — ingest-only → assisted → assisted-draft → supervised (send/finalize always human) |
| `src/soukai/operation.cljc` | **MeetingActor** — langgraph StateGraph; ingest vs assess flows |
| `src/soukai/noticeport.cljc` | **NoticeTarget** port (`fetch-convocation`/`propose-revision!`/`send!`) + soukai-owned plain-text 招集通知 builder + `mock-noticeport` (the ONLY implementation this R0 ships — see below) |
| `src/soukai/cacao.clj` | agent-side **CACAO self-mint** (JVM Ed25519 + did:key + CBOR; per-actor key) |
| `src/soukai/kotoba.clj` | wire `DatomicStore` to a kotoba-server pod (kotobase.net XRPC) |
| `src/soukai/sim.cljc` | demo driver |
| `test/soukai/*_test.clj` | propose-only contract · tally math (exact-rational boundaries) · store parity (Mem≡Datomic) · facts sanity · CACAO |

## NoticeTarget → real backend (deliberately NOT shipped in this version)

Unlike `koyomi.distribute/resend-scheduleport` (a real, `kotoba-lang/mailer`-
backed email sender), **`soukai.noticeport` ships mock-only in this
version** (ADR-2607121700 §6). `mock-noticeport` is the ONLY
`NoticeTarget` implementation here — a deterministic in-memory target so the
actor is runnable and testable with zero network/creds and zero extra
dependencies (no `kotoba-lang/mailer`, no HTTP client code, no `:local/root`
sibling checkouts — this keeps the repo buildable from an isolated scratch
directory). The protocol's shape (`fetch-convocation`/`propose-revision!`/
`send!`) is already the right shape for a future real distributor (postal
mail / email / a filing agent's API), and swapping one in is exactly what
`soukai.operation/build`'s `:noticeport` option is for — but no such
implementation exists yet, and adding one is out of scope for this version
(a documented follow-up, not a half-built stub).

```clojure
;; actor issues its own key, self-mints CACAO (same pattern as koyomi/kekkai/tayori)
(require '[soukai.kotoba :as k] '[soukai.cacao :as cacao] '[clojure.data.json :as json])
(def me    (cacao/load-or-create-identity! ".soukai/identity.edn"))
(def store (k/kotoba-store {:url "https://kotobase.net"
                            :json-write json/write-str
                            :json-read #(json/read-str % :key-fn keyword)
                            :identity me}))

;; a real secretary-LLM, mock NoticeTarget (no real distributor ships in this version)
(require '[langchain.model :as model] '[soukai.operation :as op]
         '[soukai.secretaryllm :as secretaryllm])
(op/build store
  {:advisor (secretaryllm/llm-advisor (model/anthropic-model {:api-key … :http-fn … :json-write … :json-read …}))})
```

An unparseable/hallucinating LLM response falls to confidence 0 / noop, and
**ResolutionGovernor always hold/escalates** it (no path from a malformed
LLM response to an actual send, finalize, or recorded resolution outcome).

## cloud-itonami consumption

See `90-docs/adr/2607121700-kotoba-lang-soukai-agm-actor.md`. Add
`io.github.kotoba-lang/soukai {:local/root "../../kotoba-lang/soukai"}` to
`deps.edn` for in-process use. Wiring soukai into `cloud-itonami`'s own
projection/approval layer (as `holdco`/isic-8291 do for their own domains)
is tracked as a separate follow-up, out of scope here (mirrors koyomi's own
"cloud-itonami consumption" section — pilot wiring is deliberately deferred).

## Status

Scaffold + runnable. Store is `:db-api` driven — `MemStore ≡
DatomicStore(langchain.db) ≡ kotoba-store(kotobase.net)` on the same
contract. CACAO self-issuance is offline-verified. `soukai.tally`'s
quorum/threshold math is exact-rational (no floating-point division) and
covered at every documented boundary (exactly-at, one-unit-below) for all
three resolution types. `mock-noticeport` is the only `NoticeTarget`
implementation — no real distributor exists yet (see above).
