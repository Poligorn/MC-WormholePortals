# Wormhole Portals

*Minecraft 1.21.1 · NeoForge · MIT · by Poligorn*

Nether portals become **temporary wormholes**, inspired by EVE Online.

- Portals open on their own, in linked pairs (Overworld ↔ Nether, 1:1 coordinates). You can't build, break or move them.
- Every pair has a hidden **lifetime** and a limited number of **passages**. Size hints at its class; glow and particles show how worn it is.
- When a portal dies it **implodes**, and that is fatal to anyone standing too close.
- Find signals with the **Portal Radar** (charged with blaze powder). Read a portal with the **Portal Detector**: a 5-second decryption while everyone can hear you do it.
- In the Nether, players are **anonymous**: no name tags, and `??_041`-style IDs in chat and tab.
- Optional **Create** integration: with Create installed, both tools are made on a sequenced assembly line.
- `/eveportal demo [<player>]` (permission 2) spawns a short-lived **training** portal with no Nether exit: scannable, then a safe implosion (no shards, no block damage, non-lethal).

## Сборка

```bash
./gradlew build   # build/libs/wormhole-portals-<version>.jar
```

## License

[MIT](LICENSE) © 2026 Poligorn
