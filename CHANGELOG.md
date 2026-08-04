# Changelog

## 2.1.112-Legacy-Alpha1

### Slimefun Legacy

- Replaced the Gugu Slimefun core dependency with an exact local Slimefun Legacy JAR.
- Added CI that builds Slimefun Legacy and compiles Networks against the produced artifact.
- Preserved the `Networks` plugin identity, main class, package names, configuration keys, and 288 item IDs.
- Updated the Paper API baseline to 1.21.11.
- Enforced Java 21 bytecode.

### English edition

- Added a complete `en-US` runtime locale.
- Restored classic item names and descriptions using original Blob Builds wording where available.
- Added consistent English names and functional descriptions for Expansion-only content.
- Replaced remaining hard-coded Chinese player messages and console text.
- Removed Chinese-only Pinyin/OpenCC search and runtime library loading.
- Made English the default language.

### Safety and maintenance

- Disabled automatic JAR replacement.
- Removed the Guizhan updater dependency and hard requirement.
- Replaced GuizhanLib item/material display helpers with a Networks-owned English display-name bridge.
- Changed project and support links to the maintained fork.
- Replaced the Tencent Gradle mirror with the official Gradle distribution.
- Added compatibility, localization, placeholder, item-ID, and Java bytecode verification.
- Marked Folia unsupported until a dedicated scheduler and region-ownership audit is complete.

## 2.1.112-Legacy-Alpha1 compile compatibility follow-up

- Build Slimefun Legacy with Java 25, then switch the Networks compilation to Java 21.
- Update Lombok to 1.18.46 for current JDK compatibility.
- Replace inheritance from Paper's now-final `RecipeChoice.ExactChoice` with a composition-based `RecipeChoice` implementation.
- Add permanent verifier checks for the Java 21 compile lane and recipe-choice compatibility.
