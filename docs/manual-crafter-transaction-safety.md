# Manual crafter transaction-safety follow-up

## Problem

The shared manual-crafter implementation had two mutation-order hazards.

For shaped recipes, inputs were consumed before firing `NetworkCraftEvent`. A listener cancelling the event therefore
cancelled the result after the ingredients were already removed. With multiple outputs, an earlier result could also
be pushed before a later output event cancelled the same craft.

For shapeless recipes, missing-ingredient preflight used `break` instead of failing the craft. The consumption loop
also removed entries from the same requirement map it was iterating, which can throw or leave a partially processed
recipe.

## Change

- Fire and collect all shaped output events before consuming any inputs.
- A cancellation now aborts the shaped craft before input/output mutation.
- Preserve existing output ordering, original capacity preflight, disabled-output handling, energy cost and messages.
- Missing shapeless ingredients return false immediately.
- Track shapeless remaining quantities locally instead of structurally mutating the requirement map while iterating.

This does not alter recipe definitions, item IDs, output amounts, energy costs, GUI slots or authored Expansion
Workbench recipes. Shapeless grouping retains the historical ItemStack-key semantics in this focused repair.

## Validation boundary

Regression tests cover a cancellation on the second shaped output, a missing shapeless ingredient, and a successful
two-ingredient shapeless craft. Full Legacy/United/Gugu compatibility and runtime workflows remain required before
merge. This patch does not claim crash-atomic transactions across arbitrary external inventory mutations.
