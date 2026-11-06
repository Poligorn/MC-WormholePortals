# Changelog

## Unreleased (1.0)

- Solid portal frame is no longer drawn. Frame blocks stay in the world (collision, linking, AeroPortals window matching).
- The visible rim is a single thin **neutral light-gray** (`#C9CBD1`) box in the invisible frame ring: about **0.33 block** in-plane (not a full 1-block slab) with **0.25 block** of depth toward each viewing side. Inner faces are shaded darker so the coloured interior, still on the true plane, reads recessed. Not nested outlines, not portal-coloured, not bloom.
- The rim uses cutout geometry that **writes depth**. Fast/Fancy clouds are drawn afterwards and depth-test against the main buffer; colour-only emissive rims let them show through those pixels. The rest of the collision ring stays invisible (no full-block depth wall). Foreground terrain still occludes the rim.
- Interior window renderer is unchanged. Shaderless (no shader pack, no extra light).

## 0.6

- Massive wormholes can roll up to 50×50 so airships fit; Unstable and Standard classes have their own scaled size ranges.
- Optional AeroPortals 1.21.1-1.3.6 adapter: Create Aeronautics ships pass through linked wormholes when AeroPortals and Sable are installed. No hard dependency.
- NeoForge requirement is `[21.1.235,)` so the jar loads on the pack server.
