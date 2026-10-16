# Per-machine Recipe-Aware buffer target

Network Pushers already support Recipe-Aware mode, where template stack amounts and duplicate template slots describe
the ingredient quantities for one recipe batch. Previously the number of recipe batches kept buffered at the target
came only from the server-wide `recipe_buffer_batches` item setting (default 8).

Each placed Network Pusher, Network More Pusher, and Network Best Pusher can now override that default independently
without changing the menu layout:

- **Left-click** the Recipe-Aware control: toggle Recipe-Aware mode.
- **Right-click**: enter an exact batch target from 1 to 64, using the same chat-input pattern as Advanced Transfer amount controls.
- **Shift-left**: decrease the batch target by one.
- **Shift-right**: increase the batch target by one.
- Quick adjustments wrap between 1 and 64.

The selected value is also shown directly as the stack count on the Recipe-Aware control icon, so a target of 12
visibly displays `12` in the menu.

The value is stored as block data under `recipe-aware-buffer-batches`, cached for hot-path reads, and removed from the
runtime cache when the pusher breaks. Existing placed pushers with no per-machine value continue to inherit the
server-wide `recipe_buffer_batches` default, preserving current behavior.

This changes only the Recipe-Aware target buffer. Template interpretation, routing order, transfer backoff, destination
slot discovery, item IDs, recipes, saved network data, and normal non-Recipe-Aware pusher behavior are unchanged.
