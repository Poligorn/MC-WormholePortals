# Changelog

## 0.7

- Added `/eveportal demo [<player>]`: a small unstable **training** portal for tutorials and FTB Quests.
- The demo portal is server-side only (protocol version stays `4`; no new packets, blocks or items, so 0.6 clients can join a 0.7 server).
- It has no Nether link and does not teleport or load Nether chunks. Walking in pushes the player back.
- It can be scanned normally (including the `decrypt` advancement). After a scan it warns for 5 seconds, then implodes with full visuals, sound, shake and knockback, but no block damage, no Portal Shards, and non-lethal damage that always leaves at least one heart.
- Unscanned demo portals clean themselves up after 90 seconds. A second `/eveportal demo` for the same player quietly replaces the previous one.


## 0.6

- Massive wormholes can roll up to 50×50 so airships fit; Unstable and Standard classes have their own scaled size ranges.
- Optional AeroPortals 1.21.1-1.3.6 adapter: Create Aeronautics ships pass through linked wormholes when AeroPortals and Sable are installed. No hard dependency.
- NeoForge requirement is `[21.1.235,)` so the jar loads on the pack server.
