# Changelog

## Unreleased (1.0)

- Solid portal frame is no longer drawn. Frame blocks stay in the world (collision, linking, AeroPortals window matching). A thin fullbright outline uses the portal's **assigned colour**, with a same-colour alpha halo. No white/gold contour.
- Interior window renderer is unchanged. Outline is shaderless emissive quads (not bloom, not coloured lighting, no shader pack).

## 0.6

- Massive wormholes can roll up to 50×50 so airships fit; Unstable and Standard classes have their own scaled size ranges.
- Optional AeroPortals 1.21.1-1.3.6 adapter: Create Aeronautics ships pass through linked wormholes when AeroPortals and Sable are installed. No hard dependency.
- NeoForge requirement is `[21.1.235,)` so the jar loads on the pack server.
