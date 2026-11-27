package com.eveportalnether.world.data;

import com.eveportalnether.Config;
import com.eveportalnether.world.PortalColor;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class PortalPair {
    public static final int DECAY_FRESH = 0;
    public static final int DECAY_WORN = 1;
    public static final int DECAY_CRITICAL = 2;
    public static final int DECAY_DYING = 3;

    /** After a successful scan, the training portal warns then implodes. 100 ticks = 5 seconds. */
    public static final int DEMO_WARNING_TICKS = 100;
    /** Unscanned training portals clean themselves up after 90 seconds. */
    public static final int DEMO_LIFETIME_TICKS = 20 * 90;
    /** Implosion may never drop a demo owner below one heart. */
    public static final float DEMO_MIN_HEALTH = 2.0F;

    private final UUID id;
    private final BlockPos overworldOrigin;
    private final BlockPos netherOrigin;
    private final Direction.Axis axis;
    private final int width;
    private final int height;
    private final PortalTier tier;
    private final int initialTicks;
    private final int initialJumps;
    private int ticksRemaining;
    private int jumpsRemaining;
    private int formingTicks;
    private PortalColor color;
    private int code;
    private boolean demo;
    private UUID ownerId;
    private ResourceKey<Level> demoDimension = Level.OVERWORLD;

    public PortalPair(UUID id, BlockPos overworldOrigin, BlockPos netherOrigin, Direction.Axis axis,
                      int width, int height, PortalTier tier, int ticksRemaining, int jumpsRemaining) {
        this(id, overworldOrigin, netherOrigin, axis, width, height, tier,
                ticksRemaining, jumpsRemaining, ticksRemaining, jumpsRemaining);
    }

    private PortalPair(UUID id, BlockPos overworldOrigin, BlockPos netherOrigin, Direction.Axis axis,
                       int width, int height, PortalTier tier, int initialTicks, int initialJumps,
                       int ticksRemaining, int jumpsRemaining) {
        this.id = id;
        this.overworldOrigin = overworldOrigin.immutable();
        this.netherOrigin = netherOrigin.immutable();
        this.axis = axis;
        this.width = width;
        this.height = height;
        this.tier = tier;
        this.initialTicks = Math.max(1, initialTicks);
        this.initialJumps = Math.max(1, initialJumps);
        this.ticksRemaining = ticksRemaining;
        this.jumpsRemaining = jumpsRemaining;
        this.color = tier.legacyColor();
        this.code = Math.floorMod(id.hashCode(), 1000);
    }

    public UUID getId() {
        return id;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    @javax.annotation.Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(@javax.annotation.Nullable UUID ownerId) {
        this.ownerId = ownerId;
    }

    public ResourceKey<Level> getDemoDimension() {
        return demoDimension == null ? Level.OVERWORLD : demoDimension;
    }

    public void setDemoDimension(ResourceKey<Level> demoDimension) {
        this.demoDimension = demoDimension == null ? Level.OVERWORLD : demoDimension;
    }

    public boolean existsIn(ResourceKey<Level> dimension) {
        if (isDemo()) {
            return dimension.equals(getDemoDimension());
        }
        return dimension == Level.OVERWORLD || dimension == Level.NETHER;
    }

    public int getWarningWindow() {
        return isDemo() ? DEMO_WARNING_TICKS : Config.WARNING_DURATION_TICKS.get();
    }

    /**
     * Start the short dying window so the player sees the warning, shake and implosion.
     */
    public void beginDemoCollapse() {
        if (!isDemo()) {
            return;
        }
        setFormingTicks(0);
        setTicksRemaining(DEMO_WARNING_TICKS);
        setJumpsRemaining(Math.max(2, getJumpsRemaining()));
    }

    public PortalColor getColor() {
        return color;
    }

    public void setColor(PortalColor color) {
        this.color = color;
    }

    public void setCode(int code) {
        this.code = Math.floorMod(code, 1000);
    }

    /**
     * Public designation, e.g. {@code SVF-042}: tier, colour, current stage, fixed digits.
     */
    public String getSignature() {
        return String.format("%c%c%c-%03d", tier.letter(), color.letter(), getStageLetter(), code);
    }

    public char getStageLetter() {
        if (isForming()) {
            return 'N';
        }
        return switch (getDecay()) {
            case DECAY_FRESH -> 'F';
            case DECAY_WORN -> 'W';
            case DECAY_CRITICAL -> 'C';
            default -> 'D';
        };
    }

    public int getInitialJumps() {
        return initialJumps;
    }

    public boolean isLastEntry() {
        return jumpsRemaining == 1;
    }

    public BlockPos getOverworldOrigin() {
        return overworldOrigin;
    }

    public BlockPos getNetherOrigin() {
        return netherOrigin;
    }

    public Direction.Axis getAxis() {
        return axis;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public PortalTier getTier() {
        return tier;
    }

    public int getInitialTicks() {
        return initialTicks;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }

    public int getJumpsRemaining() {
        return jumpsRemaining;
    }

    public void setJumpsRemaining(int jumpsRemaining) {
        this.jumpsRemaining = jumpsRemaining;
    }

    public void consumeJump() {
        if (jumpsRemaining > 0) {
            jumpsRemaining--;
        }
    }

    public boolean isExpired() {
        return ticksRemaining <= 0 || jumpsRemaining <= 0;
    }

    public boolean isWarning() {
        return !isForming() && ticksRemaining > 0 && ticksRemaining <= getWarningWindow();
    }

    public boolean isForming() {
        return formingTicks > 0;
    }

    public int getFormingTicks() {
        return formingTicks;
    }

    public void setFormingTicks(int formingTicks) {
        this.formingTicks = Math.max(0, formingTicks);
    }

    /**
     * While forming the window stays dim, so the moment of opening reads as ignition.
     */
    public int getDecay() {
        if (isForming() || isWarning() || isLastEntry()) {
            return DECAY_DYING;
        }
        float left = Math.min(ticksRemaining / (float) initialTicks, jumpsRemaining / (float) initialJumps);
        if (left > 0.5F) {
            return DECAY_FRESH;
        }
        return left > 0.2F ? DECAY_WORN : DECAY_CRITICAL;
    }

    public BlockPos getOrigin(ResourceKey<Level> dimension) {
        if (isDemo()) {
            return Level.NETHER.equals(getDemoDimension()) ? netherOrigin : overworldOrigin;
        }
        return dimension == Level.NETHER ? netherOrigin : overworldOrigin;
    }

    public BlockPos getStandPos(ResourceKey<Level> dimension) {
        return PortalShape.standPos(getOrigin(dimension), axis, width, height);
    }

    public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
        if (!existsIn(dimension)) {
            return false;
        }
        return PortalShape.contains(getOrigin(dimension), axis, width, height, pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.put("OverworldOrigin", NbtUtils.writeBlockPos(overworldOrigin));
        tag.put("NetherOrigin", NbtUtils.writeBlockPos(netherOrigin));
        tag.putString("Axis", axis.getName());
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        tag.putString("Tier", tier.getSerializedName());
        tag.putInt("InitialTicks", initialTicks);
        tag.putInt("InitialJumps", initialJumps);
        tag.putInt("TicksRemaining", ticksRemaining);
        tag.putInt("JumpsRemaining", jumpsRemaining);
        tag.putInt("FormingTicks", formingTicks);
        tag.putString("Color", color.getSerializedName());
        tag.putInt("Code", code);
        if (demo) {
            tag.putBoolean("Demo", true);
            if (ownerId != null) {
                tag.putUUID("Owner", ownerId);
            }
            tag.putString("DemoDimension", getDemoDimension().location().toString());
        }
        return tag;
    }

    public static PortalPair load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        BlockPos overworld = NbtUtils.readBlockPos(tag, "OverworldOrigin").orElse(BlockPos.ZERO);
        BlockPos nether = NbtUtils.readBlockPos(tag, "NetherOrigin").orElse(BlockPos.ZERO);
        Direction.Axis axis = Direction.Axis.byName(tag.getString("Axis"));
        if (axis == null || axis == Direction.Axis.Y) {
            axis = Direction.Axis.Z;
        }
        int width = Math.max(3, tag.getInt("Width"));
        int height = Math.max(4, tag.getInt("Height"));
        PortalTier tier = PortalTier.byName(tag.getString("Tier"));
        int ticks = tag.getInt("TicksRemaining");
        int jumps = tag.getInt("JumpsRemaining");
        int initialTicks = tag.contains("InitialTicks") ? tag.getInt("InitialTicks") : ticks;
        int initialJumps = tag.contains("InitialJumps") ? tag.getInt("InitialJumps") : jumps;
        PortalPair pair = new PortalPair(id, overworld, nether, axis, width, height, tier, initialTicks, initialJumps, ticks, jumps);
        pair.setFormingTicks(tag.getInt("FormingTicks"));
        pair.setColor(PortalColor.byName(tag.getString("Color"), tier.legacyColor()));
        if (tag.contains("Code")) {
            pair.setCode(tag.getInt("Code"));
        }
        if (tag.getBoolean("Demo")) {
            pair.setDemo(true);
            if (tag.hasUUID("Owner")) {
                pair.setOwnerId(tag.getUUID("Owner"));
            }
            if (tag.contains("DemoDimension")) {
                ResourceLocation dimId = ResourceLocation.tryParse(tag.getString("DemoDimension"));
                if (dimId != null) {
                    pair.setDemoDimension(ResourceKey.create(Registries.DIMENSION, dimId));
                }
            }
        }
        return pair;
    }
}
