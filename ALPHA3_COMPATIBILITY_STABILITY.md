# Alpha 3 Compatibility and Stability Release

## What was planned after Alpha 2

The documented next step after the working Alpha 2 build was production-style validation: clean restarts, Doctor scans, old-world machine checks, cargo/storage amount checks, crafting rollback checks, and chunk unload/reload testing. There was no separate written Alpha 3 feature roadmap.

Alpha 3 therefore continues from that validation point and converts the most important compatibility and lifecycle risks into code and release gates. It intentionally leaves the guide organization and gameplay content alone.

## Alpha 3 release goal

Produce one universal Networks JAR that:

- Prioritizes Slimefun Legacy
- Must compile/test against Slimefun United and Slimefun Gugu
- Preserves all established worlds and stored data
- Fails safely when an optional integration changes API
- Avoids full-registry scheduled maintenance spikes
- Cleans runtime state reliably during partial startup and normal shutdown

## Included work

### Required three-core release gate

The compatibility workflow is reusable and runs Legacy, United, and Gugu as one matrix. The universal release job depends on the complete matrix result, then compiles the artifact with the exact Legacy JAR and records the Legacy commit used.

### Runtime family fingerprinting

The runtime detector combines metadata, command aliases, and non-initializing marker-class checks through the installed Slimefun plugin classloader. Unknown variants remain blocked unless the server owner deliberately enables an unsupported-runtime override.

### Optional plugin isolation

Optional integrations have explicit soft dependencies where load order matters. Deferred APIs initialize after Bukkit has enabled plugins. `RuntimeException` and `LinkageError` failures disable only the affected integration and are included in Doctor diagnostics.

### Bounded automatic Doctor

Automatic maintenance processes a rotating maximum number of loaded nodes per pass. The default is 512 and can be changed with:

```yaml
doctor:
  max-auto-scan-entries: 512
```

Manual Doctor commands still run complete scans of loaded runtime state.

### Lifecycle cleanup

Alpha 3 resets the Doctor cursor, localization caches, optional integration singleton, shared ticker, pending first-tick locations, database cache, node registry, and controller maps. First-tick registration rechecks the loaded chunk and live Slimefun block before restoring a runtime node.

## Explicitly unchanged

- Guide order and category organization
- Plugin name and main class
- 288 Slimefun item IDs
- Recipes and machine definitions
- Persistent-data namespaces
- `CargoStorageUnits.db` path and table identity
- Existing placed machines and world records
- Java 21 Networks bytecode target
- Paper/Purpur focus

## Release checklist

- Static compatibility verifier passes.
- YAML and Python scripts parse successfully.
- Legacy matrix job passes.
- United matrix job passes.
- Gugu matrix job passes.
- Universal JAR verifier passes.
- Java 21 bytecode verifier passes.
- Staging server passes the checks in `RUNTIME_STABILITY.md`.

The GitHub compatibility matrix is the final compile authority because it builds against the current exact core sources rather than a cached or guessed API surface.
