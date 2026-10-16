# Networks 1.0.58 release candidate

This candidate combines the vanilla-inventory transaction follow-ups on top of published Networks 1.0.57.

## Included

- Failure-only changed-menu-source diagnostics already merged after 1.0.57.
- Duplication-safe furnace/brewing Vanilla Pusher destination commits already merged after 1.0.57.
- Ordinary Vanilla Pusher copy-on-write destination commits bound to the actual pusher input slot.
- Vanilla Grabber verified destination commit, source removal, rollback, partial-survivor reconciliation, and applied-removal remainder recovery.

## Preserved behavior

No item IDs, recipes, storage serialization, routing order, transfer rate, default target budgets, controller limits,
Quantum priority, empty-Quantum assignment, permissions, WildChests policy, or world-data migration changes.

Publication requires the exact combined head to pass Legacy, United, Gugu, Java 21 universal-JAR, Purpur 1.21.11
native/restart, Paper 26.3 source/native/restart, version/JAR identity, and existing release-publication guards.
