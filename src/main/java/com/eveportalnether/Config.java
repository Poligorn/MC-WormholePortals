package com.eveportalnether;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("spawn");
    }

    public static final ModConfigSpec.IntValue MAX_ACTIVE_PORTAL_PAIRS = BUILDER
            .comment("Absolute cap of portal pairs in the world")
            .defineInRange("maxActivePortalPairs", 24, 1, 1000);

    public static final ModConfigSpec.IntValue MIN_ACTIVE_PORTAL_PAIRS = BUILDER
            .comment("Cap floor, applied even with few players online")
            .defineInRange("minActivePortalPairs", 2, 0, 1000);

    public static final ModConfigSpec.DoubleValue PORTALS_PER_PLAYER_RATIO = BUILDER
            .comment("Portal pairs allowed per online player: cap = clamp(online * ratio, min, max)")
            .defineInRange("portalsPerPlayerRatio", 0.4, 0.0, 100.0);

    public static final ModConfigSpec.IntValue PORTAL_CHECK_INTERVAL_TICKS = BUILDER
            .comment("Ticks between spawn attempts (1200 = 1 minute)")
            .defineInRange("portalCheckIntervalTicks", 1200, 20, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue SPAWN_CHANCE_PER_CHECK = BUILDER
            .comment("Chance (0-1) that a spawn attempt succeeds while below the cap")
            .defineInRange("spawnChancePerCheck", 0.12, 0.0, 1.0);

    public static final ModConfigSpec.IntValue MIN_DISTANCE_BETWEEN_PORTALS = BUILDER
            .comment("Minimum horizontal distance between portal pairs")
            .defineInRange("minDistanceBetweenPortals", 300, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MIN_SPAWN_DISTANCE = BUILDER
            .comment("Minimum distance from the anchor player to a new portal")
            .defineInRange("minSpawnDistance", 500, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_SPAWN_DISTANCE = BUILDER
            .comment("Maximum distance from the anchor player to a new portal")
            .defineInRange("maxSpawnDistance", 2000, 10, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue AIR_SPAWN_CHANCE_PERCENT = BUILDER
            .comment("Chance (0-100) that a successful spawn is in mid-air instead of on the ground")
            .defineInRange("airSpawnChancePercent", 5, 0, 100);

    /**
     * Hard cap on outer frame width and height. Massive portals can roll up to this
     * so a Create Aeronautics airship can fit through the inner window (size minus 2).
     */
    public static final int PORTAL_OUTER_SIZE_CAP = 50;

    public static final ModConfigSpec.IntValue MIN_PORTAL_WIDTH = BUILDER
            .comment("Absolute minimum outer frame width applied to every class after its own range is rolled")
            .defineInRange("minPortalWidth", 4, 3, PORTAL_OUTER_SIZE_CAP);

    public static final ModConfigSpec.IntValue MAX_PORTAL_WIDTH = BUILDER
            .comment("Absolute maximum outer frame width. Massive portals need 50 so airships can pass; existing configs that still say 7 should be raised")
            .defineInRange("maxPortalWidth", PORTAL_OUTER_SIZE_CAP, 3, PORTAL_OUTER_SIZE_CAP);

    public static final ModConfigSpec.IntValue MIN_PORTAL_HEIGHT = BUILDER
            .comment("Absolute minimum outer frame height applied to every class after its own range is rolled")
            .defineInRange("minPortalHeight", 5, 4, PORTAL_OUTER_SIZE_CAP);

    public static final ModConfigSpec.IntValue MAX_PORTAL_HEIGHT = BUILDER
            .comment("Absolute maximum outer frame height. Same cap as width")
            .defineInRange("maxPortalHeight", PORTAL_OUTER_SIZE_CAP, 4, PORTAL_OUTER_SIZE_CAP);

    static {
        BUILDER.pop();
        BUILDER.push("tiers");
    }

    public static final TierSpec UNSTABLE = new TierSpec("unstable", 50, 10, 20, 2, 4, 4, 8, 5, 10);
    public static final TierSpec STANDARD = new TierSpec("standard", 40, 30, 40, 6, 10, 8, 16, 8, 20);
    public static final TierSpec MASSIVE = new TierSpec("massive", 10, 60, 90, 15, 25, 16, PORTAL_OUTER_SIZE_CAP, 16, PORTAL_OUTER_SIZE_CAP);

    static {
        BUILDER.pop();
        BUILDER.push("collapse");
    }

    public static final ModConfigSpec.DoubleValue IMPLOSION_DAMAGE = BUILDER
            .comment("Implosion damage in hearts at mid radius (1.2x at the center, 0.8x at the edge). Ignores armor, enchantments and Resistance; the default is fatal, a Totem of Undying still saves")
            .defineInRange("implosionDamage", 1000.0, 0.0, 100000.0);

    public static final ModConfigSpec.DoubleValue IMPLOSION_RADIUS = BUILDER
            .comment("Implosion radius in blocks")
            .defineInRange("implosionRadius", 6.0, 0.0, 32.0);

    public static final ModConfigSpec.IntValue WARNING_DURATION_TICKS = BUILDER
            .comment("Warning phase before a timed-out portal implodes")
            .defineInRange("warningDurationTicks", 60, 0, 1200);

    public static final ModConfigSpec.DoubleValue SHARD_DROP_CHANCE = BUILDER
            .comment("Chance (0-1) that a collapsing portal drops a Portal Shard on each loaded side")
            .defineInRange("shardDropChance", 0.12, 0.0, 1.0);

    static {
        BUILDER.pop();
        BUILDER.push("travel");
    }

    public static final ModConfigSpec.IntValue RETURN_LOCK_TICKS = BUILDER
            .comment("After travelling, the same portal refuses the player for this long (600 = 30 sec). Not applied in creative or spectator")
            .defineInRange("returnLockTicks", 600, 0, 72000);

    static {
        BUILDER.pop();
        BUILDER.push("anonymity");
    }

    public static final ModConfigSpec.BooleanValue ANONYMITY_ENABLED = BUILDER
            .comment("Hide player names in the Nether: no name tags, anonymous IDs in tab list and chat")
            .define("enabled", true);

    static {
        BUILDER.pop();
        BUILDER.push("tools");
    }

    public static final ModConfigSpec.IntValue RADAR_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown after a full radar scan")
            .defineInRange("radarCooldownTicks", 200, 0, 72000);

    public static final ModConfigSpec.IntValue RADAR_SCAN_COUNT = BUILDER
            .comment("How many wormhole signals a full radar scan lists")
            .defineInRange("radarScanCount", 5, 1, 20);

    public static final ModConfigSpec.IntValue DETECTOR_DECRYPT_TICKS = BUILDER
            .comment("How long the detector decrypts a portal signature (100 = 5 sec)")
            .defineInRange("detectorDecryptTicks", 100, 1, 1200);

    static {
        BUILDER.pop();
        BUILDER.push("effects");
    }

    public static final ModConfigSpec.IntValue FORMING_TICKS = BUILDER
            .comment("How long a new portal charges up before it can be entered (100 = 5 sec)")
            .defineInRange("formingTicks", 100, 0, 1200);

    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE = BUILDER
            .comment("Camera shake on portal opening, travel, warning and implosion")
            .define("screenShake", true);

    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE_STRENGTH = BUILDER
            .comment("Camera shake multiplier")
            .defineInRange("screenShakeStrength", 1.0, 0.0, 5.0);

    public static final ModConfigSpec.BooleanValue OPENING_LIGHTNING = BUILDER
            .comment("Harmless lightning strike when a portal opens")
            .define("openingLightning", true);

    public static final ModConfigSpec.IntValue ANNOUNCE_RADIUS = BUILDER
            .comment("Players within this distance feel a distant rumble when a portal opens (0 = off). The location is not revealed")
            .defineInRange("announceRadius", 1500, 0, 100000);

    public static final ModConfigSpec.BooleanValue RADAR_ENABLED = BUILDER
            .comment("Enable the portal radar item")
            .define("radarEnabled", true);

    public static final ModConfigSpec.BooleanValue DETECTOR_ENABLED = BUILDER
            .comment("Enable the portal detector item")
            .define("detectorEnabled", true);

    public static final ModConfigSpec.IntValue RADAR_BLAZE_POWDER_COST = BUILDER
            .comment("Blaze powder consumed when charging the radar")
            .defineInRange("radarBlazePowderCost", 1, 0, 64);

    public static final ModConfigSpec.IntValue RADAR_DURATION_TICKS = BUILDER
            .comment("Radar runtime added per successful charge (6000 = 5 minutes)")
            .defineInRange("radarDurationTicks", 6000, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static final class TierSpec {
        public final ModConfigSpec.IntValue weight;
        public final ModConfigSpec.IntValue minMinutes;
        public final ModConfigSpec.IntValue maxMinutes;
        public final ModConfigSpec.IntValue minUses;
        public final ModConfigSpec.IntValue maxUses;
        public final ModConfigSpec.IntValue minWidth;
        public final ModConfigSpec.IntValue maxWidth;
        public final ModConfigSpec.IntValue minHeight;
        public final ModConfigSpec.IntValue maxHeight;

        private TierSpec(String name, int weight, int minMinutes, int maxMinutes, int minUses, int maxUses,
                         int minWidth, int maxWidth, int minHeight, int maxHeight) {
            BUILDER.push(name);
            this.weight = BUILDER.comment("Relative spawn weight").defineInRange("weight", weight, 0, 1000);
            this.minMinutes = BUILDER.comment("Minimum lifetime in minutes").defineInRange("minMinutes", minMinutes, 1, 100000);
            this.maxMinutes = BUILDER.comment("Maximum lifetime in minutes").defineInRange("maxMinutes", maxMinutes, 1, 100000);
            this.minUses = BUILDER.comment("Minimum number of entries").defineInRange("minUses", minUses, 1, 100000);
            this.maxUses = BUILDER.comment("Maximum number of entries").defineInRange("maxUses", maxUses, 1, 100000);
            this.minWidth = BUILDER.comment("Minimum outer frame width for this class").defineInRange("minWidth", minWidth, 3, PORTAL_OUTER_SIZE_CAP);
            this.maxWidth = BUILDER.comment("Maximum outer frame width for this class").defineInRange("maxWidth", maxWidth, 3, PORTAL_OUTER_SIZE_CAP);
            this.minHeight = BUILDER.comment("Minimum outer frame height for this class").defineInRange("minHeight", minHeight, 4, PORTAL_OUTER_SIZE_CAP);
            this.maxHeight = BUILDER.comment("Maximum outer frame height for this class").defineInRange("maxHeight", maxHeight, 4, PORTAL_OUTER_SIZE_CAP);
            BUILDER.pop();
        }
    }
}
