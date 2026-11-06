# Changelog

## 0.6

- **Portal classes (outer frame):** Unstable 4–8 × 5–10, Standard 8–16 × 8–20, Massive **16–50 × 16–50** so airships can pass (inner window up to 48×48). Global config clamps default to 50. Saved `PortalPair` sizes in old worlds are unchanged; existing configs that still set `maxPortalWidth = 7` keep clamping until raised.
- **Optional AeroPortals adapter** (`1.21.1-1.3.6`, FML `[1.3.0,2.0.0)`): Create Aeronautics / Sable ships can transfer through linked wormholes when AeroPortals and Sable are installed. `compileOnly`, no hard dependency, not bundled. Wormholes still loads without them.
- **NeoForge** requirement is `[21.1.235,)` so the jar loads on the pack server. Compile target remains 21.1.248.
