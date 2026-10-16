# Changed-menu-source diagnostics

Networks keeps its strict source snapshot and compensation guard unchanged.

When a Slimefun menu source changes between the detached network offer and the source-side commit, the warning now
adds failure-only context:

- source machine Slimefun ID when available;
- source menu slot;
- coarse change kind: type, amount, metadata, removal, or a combination;
- before/after item identity and count, preferring the Slimefun item ID and falling back to Bukkit material.

No display names, lore, PDC values, player identifiers, or item payloads are logged. Diagnostic lookup failures fall
back to safe generic values and cannot interrupt compensation.

This adds no work to successful transfers and does not change source matching, deposit, compensation, VOID, routing,
or storage behavior.
