# Contributing to cloud-itonami-isic-0892

Contributions should preserve the actor's scope: back-office coordination only,
with CRITICAL exclusions of direct extraction-equipment control, bog-drainage
infrastructure control, and environmental-permit-issuing-authority decisions
(see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: clojure -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Direct extraction-equipment control — milling/harrowing/ridging machine
  control, vacuum-harvester control (milled peat), or sod-cutting/block-
  cutting machine control, baling-machine control (sod/block-cut peat).
- Bog-drainage infrastructure control (drainage-ditch/sluice-gate/water-
  table control).
- Environmental-permit-issuing-authority decisions (permits, licenses,
  compliance enforcement).

Contributions that cross these boundaries will be rejected.
