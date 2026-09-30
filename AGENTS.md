# Networks maintenance contract

Maintain the existing Networks gameplay and saved data while modernizing for Minecraft 1.21.11 and newer. Prioritize Paper and Purpur; retain appropriate Leaf/Folia validation. The server version floor is not an age cutoff for item, blueprint, quantum-storage or network data.

Preserve item and research IDs, recipes, stack counts, typed persistent keys, storage identities and contents, crafting throughput, ingredient buffering, transfer order, priorities, and atomic deposit/withdrawal behavior. Do not rewrite BlockStorage or serialization merely to remove a warning. Do not substitute fresh item templates for existing player items. Keep optional integrations lazy and retain compatibility with the supported Legacy/United/Gugu API shapes. Source compatibility is not proof of cross-fork saved-world rollback.

Use the Gradle wrapper and a known exact Slimefun core JAR:

```sh
./gradlew clean build --no-daemon -PslimefunCoreJar=/absolute/path/core.jar -PjegJar=/absolute/path/SF_JustEnoughGuide2.1.67.jar -PstrictDeprecation=true
python3 scripts/verify_legacy_compatibility.py
python3 scripts/verify_java21_bytecode.py build/libs/SF_Networks1.0.45.jar
python3 scripts/verify_universal_jar.py build/libs/SF_Networks1.0.45.jar 1.0.45
```

The current build uses a Java 25 toolchain and emits Java 21 bytecode. Retain the historical JAR property aliases. Keep version, CI output names and the release contract synchronized when preparing an actual version bump; do not publish a new stable release from an unvalidated development patch. Deliver raw plugin JARs, not source archives disguised as installable plugins.

Work in coherent tested batches. Verify branch heads before writing, preserve concurrent changes, and do not automatically merge or publish. Keep new source comments, diagnostics and documentation in English. Record exact tested source/core revisions, real test results and remaining runtime/data limitations. Do not advertise source-search matches as compiler warnings or operation-count reductions as measured server TPS gains.
