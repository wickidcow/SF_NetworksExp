# Quantum restart probe entity-registration wait

The two-process Quantum persistence probe validates persisted block data and menu inventory before it invokes the
real break handler. Its final assertion then verifies that both the filled Quantum Storage item and the physical
output-slot contents appear as world item entities.

A fixed two-tick post-break delay was flaky on hosted CI: the direct storage item could already be visible while
`BlockMenu.dropItems` output entities had not yet appeared in Paper's nearby-entity index. This produced a false
negative even though the same run had just verified the persisted 64-item output stack.

The probe now polls once per server tick for at most 40 ticks and finishes immediately when both expected drop types
are visible. The deadline is still strict; missing output continues to fail and the same quantity/metadata checks run
before cleanup. Plugin runtime code, Quantum break behavior, saved data and release artifacts are unchanged.
