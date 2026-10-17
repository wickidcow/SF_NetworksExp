# Networks withdrawal transaction integration (draft)

**Not a production release.** This branch composes already proposed fixes without changing IDs, storage schemas, transfer routing or Java 21 baseline.

Exact inputs:
- Recipe-Aware machine + menu write verification: `0351ec4c028f8066a95cd99763793de4e18c30fa` (PR #86)
- Observed player cursor commits: `22930060aea1c734521666b1169834b51509f650` (PR #87)
- Observed player storage commits and callback preflight: `47d6ef17833002b009d0672c093d4c876a511670` (PR #88)

## What must be verified
1. Legacy / United / Gugu source contracts, Java 21 bytecode and universal artifact.
2. Purpur 1.21.11 native manager, persisted Quantum restart.
3. Paper 26.3 native manager and persisted Quantum restart.
4. Player Grid cursor and ordinary player inventory withdrawals: empty, partial, full, write-then-throw, silent reject and third-party inventory changes.
5. Recipe-Aware OFF behavior identical to existing transfer order, ON keeping template ratios where the target accepts items.
6. Paper 26.3 build used by AlbionMC and a representative IE2 2.0.14 Dust Extractor reproduction.
7. Compare /sf tick top or Spark captures for busy Networks machines.

Do not advertise complete anti-duplication guarantees for adapters that do not provide verifiable rollback receipts. In particular, network-to-network transfer receipts and partial source-root rollback remain outside the scoped implementation. Retain full backups and do not auto-merge or overwrite the canonical release.

The CI artifact is still named for the existing version **1.0.58** until a separately validated version bump. Do not confuse it with the public stable 1.0.58 JAR.
