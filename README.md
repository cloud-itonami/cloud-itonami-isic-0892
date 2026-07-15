# cloud-itonami-isic-0892

Open Business Blueprint for **ISIC Rev.4 0892**: Extraction of peat — an
ISIC Wave 3 (production/mining) operations-coordination actor per
ADR-2607121000. Back-office and coordination workflow for peat-
extraction sites, modeled closely on `cloud-itonami-isic-0893`'s
(Extraction of salt) governed-actor discipline.

**Maturity: `:implemented`** — PeatOpsAdvisor ⊣ PeatExtractionGovernor
as a langgraph-clj StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt). All source `.cljc` (portable
to JVM / ClojureScript / GraalVM), no JVM-only interop.

## Scope: both ISIC-0892 extraction methods

ISIC 0892 covers more than one extraction method, and this actor
coordinates the back office of either kind of site:

- **Milled peat** — mechanical harrowing/ridging of the bog surface and
  vacuum-harvesting of the dried, milled peat.
- **Sod/block-cut peat** — cutting and lifting solid blocks of peat from
  the bog face, then field-drying.

The actor is deliberately method-agnostic at the coordination layer
(extraction-record logging, extraction-operation scheduling,
environmental-concern flagging, shipment coordination apply to either
method); its governor's scope exclusions cover both methods'
equipment-control territory explicitly (see below).

## CRITICAL: Scope Exclusions

This actor **DOES NOT** and **NEVER WILL**:

- **Direct extraction-equipment control** — milling/harrowing/ridging
  machine control, vacuum-harvester control (milled peat) — or sod-
  cutting/block-cutting machine control, baling-machine control
  (sod/block-cut peat) — and, for either method, bog-drainage-
  infrastructure control (drainage-ditch/sluice-gate/water-table
  control)
- **Environmental-permit-issuing-authority decisions** — wetland-
  impact permit issuance, drainage-license suspension, or compliance
  enforcement

This actor **only** coordinates back-office operations: extraction-
record logging (harvest-volume/moisture-content), harvest/drying/
baling operation scheduling, environmental-concern flagging (bog-
drainage/wetland-impact/fire-risk, for either method — always routed
to a human), and outbound baled-peat shipment coordination. Every
proposal the advisor drafts carries `:effect :propose` — never a
direct actuation — and `peatops.governor` independently re-scans every
proposal's content for the excluded scope areas above, regardless of
op or confidence.

## Operations

Closed proposal-op allowlist (`peatops.governor/allowed-ops`), all
`:effect :propose`:

- `:log-extraction-record` — harvest-volume/moisture-content data logging
- `:schedule-extraction-operation` — harvest/drying/baling scheduling proposal
- `:flag-environmental-concern` — surface a bog-drainage/wetland-impact/
  fire-risk concern — **ALWAYS escalates**
- `:coordinate-shipment` — outbound baled-peat shipment coordination

**HARD invariants** (always `:hold`, never human-overridable):

1. **Site unverified** — the target site record (site + environmental
   permit) must exist AND be independently `:registered?`/`:verified?`
   in the store before any proposal for it may commit or even
   escalate.
2. **Effect not `:propose`** — any proposal whose `:effect` is not
   `:propose` is, by construction, a claim to directly actuate outside
   governance.
3. **Scope exclusion** — any proposal (regardless of op) outside the
   closed allowlist, or whose rationale/summary/citations/value touches
   extraction-equipment-control (either method family) or
   environmental-permit-issuing-authority territory, is a permanent,
   un-overridable block. Evaluated unconditionally on every proposal.

**ESCALATE** (always human sign-off, when the governor is otherwise clean):

- `:flag-environmental-concern` — always, regardless of confidence.
- Low advisor confidence (`< 0.6`).

## Rollout phases (`peatops.phase`)

Phase 0 (read-only) → 1 (extraction-record logging, approval-gated) →
2 (adds extraction-operation scheduling + shipment coordination,
approval-gated) → 3 (supervised auto: extraction-record/extraction-
operation/shipment may auto-commit when governor-clean and confident).
`:flag-environmental-concern` is deliberately absent from every
phase's `:auto` set — a permanent structural fact, not a rollout
milestone still to come — matching `peatops.governor`'s own
`always-escalate-ops` independently.

## Development

```bash
clojure -M:test   # run the full suite
clojure -M:run    # walk the demo scenarios (peatops.sim)
clojure -M:lint    # clj-kondo
```

AGPL-3.0-or-later, forkable by any qualified operator. Part of cloud-itonami.
