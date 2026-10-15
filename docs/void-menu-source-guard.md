# VOID menu source-commit guard

## Report and source baseline

Reported on Networks 1.0.55, Slimefun Legacy 4.1.74 and Purpur 1.21.11 build 2568: a menu source changed after four items were accepted, and the four items were withdrawn as compensation. The stack reached `LineOperationUtil.grabItem` line 448. On baseline `56b447a535502c0f82bc94e0d734e8d4aad85f9f`, that line is the VOID grab branch, whose unconditional source clear ran even when the transfer helper returned zero after compensation.

## Changes

- Retain all existing integer-returning deposit entry points and their accepted-count reconciliation.
- Preserve an explicit failure flag from the detached offer, including an exception after partial acceptance.
- Add `moveMenuSlotIntoNetworkAndVoidRemainder` with a typed result. Intentional VOID cleanup occurs inside the source-commit guard, not in a second caller-side write.
- Check the original source before clearing even when an otherwise clean network offer accepted zero items.
- On an offer exception, commit only the accepted amount and preserve any unaccepted source remainder. On a source conflict or failed source write, retain the existing compensation and guarded-restoration behavior.
- Abort the current menu's VOID grab pass on failure instead of touching later slots.
- Nonpositive transfer limits and invalid/empty slots do not authorize deletion. Clean positive-limit VOID offers retain intentional overflow disposal.

No item IDs, recipes, serialization, storage identities, saved-data migrations, or network ordering are changed. The release candidate now uses the distinct version 1.0.56 and canonical filename SF_Networks1.0.56.jar. It must complete fresh validation and receive merge/publication approval before being described as a stable release. The published 1.0.55 asset must remain unchanged.

## Regression coverage and validation status

`NetworkVoidTransferTest` adds 30 parameterized/test cases using the actual `LineOperationUtil.grabItem` caller and real MockBukkit item metadata. Coverage includes source type, quantity, metadata and empty-state changes; zero/partial/full acceptance; runtime/linkage deposit failures; failed writes and dirty marking; external replacements during writes; incomplete compensation; successful VOID overflow behavior; transfer limits; and unchanged ordinary menu remainder behavior.

The original c25223304bca8683be61b389f037a7ca60ee65e6 source passed workflow 38767266503 with 236 primary-project tests, including all 30 VOID cases, and the existing compatibility/native/restart checks. Those results do not certify the later snapshot follow-up or this separately versioned 1.0.56 candidate; fresh exact-head checks are required. See docs/dust-extractor-source-snapshot.md for the additional 14 authored regression cases. The editing container could not resolve GitHub or download Gradle dependencies, so a full project build was not run locally. Baseline file bytes were checked against their Git blob hashes, and the isolated patch is submitted for the existing compatibility, full-build, Java-21-bytecode and native-staging CI gates. Consult the PR check results for actual outcomes and exact tested core revisions.

## Scope and remaining limitations

This fixes the destructive caller behavior exposed by the report. It does not identify which storage callback changed the source in that server or guarantee the source-conflict warning disappears. A four-accepted/four-compensated log does not establish historical item conservation across third-party callbacks, and the patch cannot reconstruct previously lost items.

The separate virtual NetworksDrawer VOID path and network-to-menu VOID push path are not changed or certified by this focused menu-source fix. Incomplete compensation remains a visible failure, never a claim of successful rollback. Real loaded-world behavior, third-party inventory integrations and all server/version combinations require appropriate staging validation.

## Operational precaution

Pause the transfer reading the reported source while investigating; do not delete the storage or weaken the source-integrity check. Identify the source block and connected transfer configuration. Before installing an approved, uniquely versioned replacement, stop the server cleanly and take a consistent backup of worlds and plugin data. Replace only the intended Networks JAR, restart normally, and verify a small controlled transfer before resuming the line. Do not use a live plugin reload or overwrite the existing published asset.
