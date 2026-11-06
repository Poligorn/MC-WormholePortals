# Changelog

## 1.0.0-alpha.1

Not a stable release. Do not merge, tag, or publish.

- **Portal hum restored.** 0.6 particle throttling also starved `PORTAL_AMBIENT` one-shots on 7×8+ windows. Live windows now play one looping drone per aperture; it stops on collapse/dimension change.
- **Scan through-view (optional Immersive Portals 6.0.7).** After a successful detector scan the scanning player gets a 60s live destination view (`teleportable=false`). 2s ease open/close. Vortex stays opaque until dest sections are compiled; `AFTER_LEVEL` overlay so IP dest cannot cover the iris. IP quad tucks under the 0.03 white rim. Client clock uses ticks, not dimension `gameTime`. Dest-side vortex skip is only for this revealing window. Leftover tagged IP entities are cancelled. Logout/stop despawns them.
- **Dedicated-server safety.** Client IP lookups (`ClientWorldLoader`, `PortalRendering`) no longer live on the common adapter class.
- Approved look unchanged: opaque portal-tinted pixel vortex, 0.03 fullbright white rim, halo, updated detector preview.
- Massive 16–50 geometry: stand column stays interior; dest aperture extra 0.08 under the rim.

### Optional integrations (not claimed fixed)

- Official Immersive Portals 6.0.7 + Sable 2.0.6 still crash at startup (mixin `Redirect` collision). AeroPortals 1.3.6 requires Sable. Independent of this jar.
- Supported configs: **IP + Cloth, no Sable** for live through-view; **Sable + AeroPortals, no official IP** for Aeronautics ships. Do not load both official IP and Sable.
- Seamless IP teleport and ship flight through wormholes are backlog, not this alpha.

## 0.6

- Massive wormholes can roll up to 50×50 so airships fit; Unstable and Standard classes have their own scaled size ranges.
- Optional AeroPortals 1.21.1-1.3.6 adapter: Create Aeronautics ships pass through linked wormholes when AeroPortals and Sable are installed. No hard dependency.
- NeoForge requirement is `[21.1.235,)` so the jar loads on the pack server.
