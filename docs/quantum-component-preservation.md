# Quantum storage presentation without legacy lore loss

This follows the reconciled Networks source `41befeb633baabd1e8ee6bc2fdd2b3a66694b643` and the coordinated Legacy core merge `9e8de71adf70e9a361a74c21afe5e7006c85faf6`. The existing accessor-cache, transaction and pusher-registration changes remain intact.

QuantumCache's two lore methods now use native ItemMeta component lore. Existing prefix components remain the same immutable values instead of being serialized to legacy text and parsed back. Only generated storage status lines pass through the existing localized TextUtil converter. The same spacer, tail positions, missing-line padding, counts, limits and optional limit-line behavior are retained. Updating a normal existing lore now reads the list once rather than up to three times. This is an operation-count reduction, not a measured TPS claim.

No item/research IDs, storage quantities, limits, void behavior, item templates, deposit/withdrawal code, transfer priorities, crafting rates, recipes, serialized data, resource-pack metadata or public descriptors change. The class no longer needs its deprecation suppression. MockBukkit is test-only and is not shipped.

## Validation

Run `36798950819` built the exact coordinated core and reproduced the previous rich-lore loss by restoring the original QuantumCache while retaining the new regression. The failure was an actual component-equality assertion involving a translatable component, not a compilation or fixture error. The corrected full project passed **81 tests**, including **10 new presentation cases**, with zero failures/errors/skips. Strict deprecation/removal compilation, the existing 289-ID/source guards and both universal-JAR/Java21 checks passed.

The tests cover translation/font/hover/insertion preservation, null and short old lore, custom/ordinary tail layouts, large long-valued quantities, existing PDC/model metadata, unchanged stored item identity, repeated updates, localization and **250 deterministic old-layout comparisons** against the former algorithm. Tests use real MockBukkit item metadata and QuantumCache; localization is the substituted boundary. They are not live-player or full historical-world tests.

Downloaded evidence artifact `11135331252` matched SHA256 `aff46d29cb88d5b0f96ad18ad700da79d3c3c6cc53d0286773c38a46485c9148`. Actual XML counts and the three promoted source blobs matched the reviewed local files. The input core JAR SHA256 was `86c19f0d8997e00df24b902cff38ebb085d0f237714eb748c951de418e867991` from the pinned source above. Candidate JAR artifact `11135201650` has SHA256 `44b6d8e6689fe755e5bf70626499247c9332060e8d78850274efc888c96db310`.

Only three validated source/build/test files and this document are promoted. The temporary validation workflow and compressed patch are excluded. Normal PR validation and the exact coordinated bundle remain separate checks; no merge, stable release or version bump is implied.
