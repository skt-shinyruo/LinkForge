# Maven Module Governance Pilot

This is the measured decision record for the request to reconsider the five-layer Maven split in
`server/governance`. It deliberately records a decision before any module merge; package structure and
ArchUnit rules remain the ownership mechanism until a measurable benefit is demonstrated.

## Baseline (2026-08-10)

- The server contains 52 POM projects and 51 `<module>` declarations (including the integration-test entry in
  the `it` profile).
- Governance has 5 Maven modules: `domain`, `application`, `infrastructure`, `interfaces`, and `runtime`.
- The Governance context currently contains 29 production Java files and 1,597 lines, including real
  persistence adapters, controllers, ports, and runtime composition. It is not a single empty wrapper.
- Cold compile timings on this workspace:

```text
mvn -q clean compile                               6.37 s
mvn -q -pl :linkforge-governance-runtime -am clean compile  5.07 s
```

The slice timing includes its transitive foundation and contract modules, so it is not a claim that merging
Governance would save 1.30 seconds. It is a baseline for repeated measurements on the same machine and JDK.

## Decision

Keep the bounded-context module split for now. A merge would move dependency and layer constraints into every
package owner before the pilot has shown a meaningful build or maintenance benefit. The current runtime module
is a useful composition seam and the existing Architecture tests protect its explicit imports.

## Revisit gate

Repeat the two commands above after a representative dependency or test change, three times with a clean local
Maven repository state recorded separately. Reconsider a merge only when all of the following are true:

1. Governance contributes less than 10 production classes and has no independent adapter or interface behavior;
2. Its cold slice is at least 20% of the cold full-reactor time; and
3. A package-level ArchUnit replacement test can enforce the same dependency direction without weakening the
   current runtime composition and integration-test topology.

Until then, adding a new context-level module requires an owner, an adapter or port with a second implementation,
and a measured reason. This keeps the 52-module concern visible without turning it into an unverified bulk merge.
