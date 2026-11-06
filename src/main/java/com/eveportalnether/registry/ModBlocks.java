package com.eveportalnether.registry;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EvePortalNether.MODID);

    public static final DeferredBlock<Block> WORMHOLE = BLOCKS.register("wormhole", 
        () -> new WormholeBlock(BlockBehaviour.Properties.of()
            .mapColor(state -> state.getValue(WormholeBlock.FRAME) ? MapColor.COLOR_BLACK : MapColor.COLOR_PURPLE)
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
            .noOcclusion()
            .isSuffocating((state, getter, pos) -> state.getValue(WormholeBlock.FRAME))
            .isViewBlocking((state, getter, pos) -> false)
            .isValidSpawn((state, getter, pos, type) -> false)
            .pushReaction(PushReaction.BLOCK)
            .lightLevel(WormholeBlock::lightLevel)
        )
    );

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
