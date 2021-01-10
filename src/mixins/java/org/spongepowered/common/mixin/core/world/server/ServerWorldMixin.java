/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.core.world.server;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.CustomServerBossInfoManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.listener.IChunkStatusListener;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.ISpecialSpawner;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.IServerWorldInfo;
import net.minecraft.world.storage.SaveFormat;
import net.minecraft.world.storage.ServerWorldInfo;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.registry.RegistryHolder;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.SerializationBehavior;
import org.spongepowered.api.world.WorldType;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeCommon;
import org.spongepowered.common.block.SpongeBlockSnapshotBuilder;
import org.spongepowered.common.bridge.ResourceKeyBridge;
import org.spongepowered.common.bridge.world.PlatformServerWorldBridge;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkBridge;
import org.spongepowered.common.bridge.world.storage.ServerWorldInfoBridge;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.phase.general.GeneralPhase;
import org.spongepowered.common.mixin.core.world.WorldMixin;
import org.spongepowered.common.registry.SpongeRegistryHolder;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends WorldMixin implements ServerWorldBridge, PlatformServerWorldBridge, ResourceKeyBridge {

    // @formatter:off
    @Shadow @Nonnull public abstract MinecraftServer shadow$getServer();
    @Shadow protected abstract void shadow$saveLevelData();
    // @formatter:on

    private SaveFormat.LevelSave impl$levelSave;
    private CustomServerBossInfoManager impl$bossBarManager;
    private SpongeRegistryHolder impl$registerHolder;
    private IChunkStatusListener impl$chunkStatusListener;
    private Map<Entity, Vector3d> impl$rotationUpdates;

    private boolean impl$isManualSave = false;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void impl$cacheLevelSave(final MinecraftServer p_i241885_1_, final Executor p_i241885_2_, final SaveFormat.LevelSave p_i241885_3_,
            final IServerWorldInfo p_i241885_4_, final RegistryKey<World> p_i241885_5_, final DimensionType p_i241885_6_, final IChunkStatusListener p_i241885_7_,
            final ChunkGenerator p_i241885_8_, final boolean p_i241885_9_, final long p_i241885_10_, final List<ISpecialSpawner> p_i241885_12_, final boolean p_i241885_13_,
            final CallbackInfo ci) {
        this.impl$levelSave = p_i241885_3_;
        this.impl$chunkStatusListener = p_i241885_7_;
        this.impl$rotationUpdates = new Object2ObjectOpenHashMap<>();
        this.impl$registerHolder = new SpongeRegistryHolder(((DynamicRegistries.Impl) p_i241885_1_.registryAccess()));
    }

    @Override
    public SaveFormat.LevelSave bridge$getLevelSave() {
        return this.impl$levelSave;
    }

    @Override
    public IChunkStatusListener bridge$getChunkStatusListener() {
        return this.impl$chunkStatusListener;
    }

    @Override
    public boolean bridge$isLoaded() {
        if (((WorldBridge) this).bridge$isFake()) {
            return false;
        }

        final ServerWorld world = this.shadow$getServer().getLevel(this.shadow$dimension());
        if (world == null) {
            return false;
        }

        return world == (Object) this;
    }

    @Override
    public void bridge$adjustDimensionLogic(final DimensionType dimensionType) {
        if (this.bridge$isFake()) {
            return;
        }

        super.bridge$adjustDimensionLogic(dimensionType);

        // TODO Minecraft 1.16.2 - Rebuild level stems, get generator from type, set generator
        // TODO ...or cache generator on type?
    }

    @Override
    public CustomServerBossInfoManager bridge$getBossBarManager() {
        if (this.impl$bossBarManager == null) {
            if (World.OVERWORLD.equals(this.shadow$dimension()) || this.bridge$isFake()) {
                this.impl$bossBarManager = this.shadow$getServer().getCustomBossEvents();
            } else {
                this.impl$bossBarManager = new CustomServerBossInfoManager();
            }
        }

        return this.impl$bossBarManager;
    }

    @Override
    public void bridge$addEntityRotationUpdate(final net.minecraft.entity.Entity entity, final Vector3d rotation) {
        this.impl$rotationUpdates.put(entity, rotation);
    }

    @Override
    public void bridge$updateRotation(final net.minecraft.entity.Entity entityIn) {
        final Vector3d rotationUpdate = this.impl$rotationUpdates.get(entityIn);
        if (rotationUpdate != null) {
            entityIn.xRot = (float) rotationUpdate.getX();
            entityIn.yRot = (float) rotationUpdate.getY();
        }
        this.impl$rotationUpdates.remove(entityIn);
    }

    @Override
    public void bridge$triggerExplosion(Explosion explosion) {
        // Sponge start
        // Set up the pre event
        if (ShouldFire.EXPLOSION_EVENT_PRE) {
            final ExplosionEvent.Pre
                    event =
                    SpongeEventFactory.createExplosionEventPre(PhaseTracker.getCauseStackManager().getCurrentCause(),
                            explosion, (org.spongepowered.api.world.server.ServerWorld) this);
            if (SpongeCommon.postEvent(event)) {
                return;
            }
            explosion = event.getExplosion();
        }

        final net.minecraft.world.Explosion mcExplosion = (net.minecraft.world.Explosion) explosion;

        try (final PhaseContext<?> ignored = GeneralPhase.State.EXPLOSION.createPhaseContext(PhaseTracker.SERVER)
                .explosion((net.minecraft.world.Explosion) explosion)
                .source(explosion.getSourceExplosive().isPresent() ? explosion.getSourceExplosive() : this)) {
            ignored.buildAndSwitch();
            final boolean shouldBreakBlocks = explosion.shouldBreakBlocks();
            // Sponge End

            mcExplosion.explode();
            mcExplosion.finalizeExplosion(true);

            if (!shouldBreakBlocks) {
                mcExplosion.clearToBlow();
            }

            // Sponge Start - end processing
        }
        // Sponge End
    }

    @Override
    public void bridge$setManualSave(final boolean state) {
        this.impl$isManualSave = state;
    }

    @Override
    public RegistryHolder bridge$registries() {
        return this.impl$registerHolder;
    }

    @Override
    public BlockSnapshot bridge$createSnapshot(final int x, final int y, final int z) {
        final BlockPos pos = new BlockPos(x, y, z);

        if (!World.isInWorldBounds(pos)) {
            return BlockSnapshot.empty();
        }

        if (!this.hasChunk(x >> 4, z >> 4)) {
            return BlockSnapshot.empty();
        }
        final SpongeBlockSnapshotBuilder builder = SpongeBlockSnapshotBuilder.pooled();
        builder.world((ServerWorld) (Object) this).position(new Vector3i(x, y, z));
        final net.minecraft.world.chunk.Chunk chunk = this.shadow$getChunkAt(pos);
        final net.minecraft.block.BlockState state = chunk.getBlockState(pos);
        builder.blockState(state);
        final net.minecraft.tileentity.TileEntity blockEntity = chunk.getBlockEntity(pos, net.minecraft.world.chunk.Chunk.CreateEntityType.CHECK);
        if (blockEntity != null) {
            TrackingUtil.addTileEntityToBuilder(blockEntity, builder);
        }
        ((ChunkBridge) chunk).bridge$getBlockCreatorUUID(pos).ifPresent(builder::creator);
        ((ChunkBridge) chunk).bridge$getBlockNotifierUUID(pos).ifPresent(builder::notifier);

        builder.flag(BlockChangeFlags.NONE);
        return builder.build();
    }

    @Override
    public ResourceKey bridge$getKey() {
        return (ResourceKey) (Object) this.shadow$dimension().location();
    }

    @Redirect(method = "saveLevelData", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getWorldData()Lnet/minecraft/world/storage/IServerConfiguration;"))
    private IServerConfiguration impl$usePerWorldLevelDataForDragonFight(final MinecraftServer server) {
        return (IServerConfiguration) this.getLevelData();
    }

    /**
     * @author zidane - December 17th, 2020 - Minecraft 1.16.4
     * @reason Honor our serialization behavior in performing saves
     */
    @Overwrite
    public void save(@Nullable final IProgressUpdate progress, final boolean flush, final boolean skipSave) {
        final ServerWorldInfo levelData = (ServerWorldInfo) this.getLevelData();

        final ServerChunkProvider chunkProvider = ((ServerWorld) (Object) this).getChunkSource();

        if (!skipSave) {

            final SerializationBehavior behavior = ((ServerWorldInfoBridge) levelData).bridge$serializationBehavior();

            if (progress != null) {
                progress.progressStartNoAbort(new TranslationTextComponent("menu.savingLevel"));
            }

            // We always save the metadata unless it is NONE
            if (behavior != SerializationBehavior.NONE) {

                this.shadow$saveLevelData();

                // Sponge Start - We do per-world WorldInfo/WorldBorders/BossBars

                levelData.setWorldBorder(this.getWorldBorder().createSettings());

                levelData.setCustomBossEvents(((ServerWorldBridge) this).bridge$getBossBarManager().save());

                ((ServerWorldBridge) this).bridge$getLevelSave().saveDataTag(SpongeCommon.getServer().registryAccess()
                    , (ServerWorldInfo) this.getLevelData(), this.shadow$dimension() == World.OVERWORLD ? SpongeCommon.getServer().getPlayerList()
                        .getSingleplayerData() : null);

                // Sponge End
            }
            if (progress != null) {
                progress.progressStage(new TranslationTextComponent("menu.savingChunks"));
            }

            final boolean canAutomaticallySave = !this.impl$isManualSave && behavior == SerializationBehavior.AUTOMATIC;
            final boolean canManuallySave = this.impl$isManualSave && behavior == SerializationBehavior.MANUAL;

            if (canAutomaticallySave || canManuallySave) {
                chunkProvider.save(flush);
            }
        }

        this.impl$isManualSave = false;
    }

    @Override
    public String toString() {
        return new StringJoiner(",", ServerWorld.class.getSimpleName() + "[", "]")
                .add("key=" + this.shadow$dimension())
                .add("worldType=" + ((WorldType) this.shadow$dimensionType()).key(RegistryTypes.WORLD_TYPE))
                .toString();
    }
}
