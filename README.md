# Networks Legacy

**Networks Legacy** is an English-first, actively maintained continuation of the original **Networks** addon for **Slimefun Legacy**.

It preserves the original plugin identity, item IDs, packages, and storage behavior while bringing the modern NetworksExpansion feature set to current Paper and Purpur servers.

## Highlights

- Complete network-based item storage and transport
- Network Controllers, Bridges, Grids, Importers, Exporters, Pushers, and Grabbers
- Quantum Storage, Network Drawers, managers, viewers, and crafting systems
- P2P, wireless, power, blueprint, and advanced transfer features from NetworksExpansion
- English item names and descriptions based on Sefiraat's original Blob Builds wording
- Exact Slimefun Legacy compile-time compatibility
- Java 21 bytecode for Java 21 and newer runtimes
- Automatic JAR replacement disabled

## Compatibility

| Platform | Status |
|---|---|
| Slimefun Legacy | Required |
| Paper / Purpur 1.21.x and 26.x | Primary target |
| Java 21+ | Supported bytecode target |
| Folia | Not yet supported |
| Original Networks data | Preserved by design; staging backup test required |

This first Legacy alpha intentionally avoids changing item IDs, plugin identity, storage keys, or database formats.

## Building

Networks Legacy must be compiled against an exact Slimefun Legacy JAR:

```bash
./gradlew clean build \
  -PslimefunLegacyJar=/path/to/Slimefun-4.1.16.jar
```

The shaded plugin will be created in `build/libs/`.

GitHub Actions builds Slimefun Legacy first, then compiles Networks against that exact artifact and verifies Java 21 bytecode.

## Credits

- **Sefiraat** — original Networks project and classic English wording
- **SlimefunGuguProject contributors** — continued compatibility work
- **ytdd9527, balugaq, yitoudaidai, tinalness, and contributors** — NetworksExpansion development
- **wickidcow** — Slimefun Legacy maintenance and Albion compatibility

Networks Legacy remains licensed under the GNU General Public License v3.0.

## Safety

Back up the server, Networks configuration, and Slimefun data before replacing an existing Networks build. Test existing Controllers, Grids, Drawers, Quantum Storage, blueprints, and wireless links on a staging copy first.
