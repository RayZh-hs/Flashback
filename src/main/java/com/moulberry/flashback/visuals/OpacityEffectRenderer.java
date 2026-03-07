package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.FramebufferUtils;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.ext.LevelChunkExt;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.effect.BlockEffectManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class OpacityEffectRenderer {

    private static RenderTarget baseTarget = null;
    private static RenderTarget accumulationTargetA = null;
    private static RenderTarget accumulationTargetB = null;
    private static RenderTarget passTarget = null;

    private static boolean renderingMultipass = false;
    private static boolean renderWorldHookThisPass = true;
    private static boolean forceSynchronousChunkRebuilds = false;

    public static boolean shouldRenderWorldHook() {
        return !renderingMultipass || renderWorldHookThisPass;
    }

    public static boolean shouldForceSynchronousChunkRebuilds() {
        return forceSynchronousChunkRebuilds;
    }

    public static boolean renderWithOpacityPasses(LevelRenderer levelRenderer, GraphicsResourceAllocator graphicsResourceAllocator,
                                                  DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                                  Matrix4f frustumMatrix, Matrix4f projectionMatrix, Matrix4f projection,
                                                  GpuBufferSlice gpuBufferSlice, Vector4f clearColour, boolean drawBlockOutline) {
        if (renderingMultipass) {
            return false;
        }

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.opacityPreviewMode != OpacityPreviewMode.LINEAR_INTERPOLATION) {
            return false;
        }

        ReplayServer replayServer = Flashback.getReplayServer();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel clientLevel = minecraft.level;
        if (replayServer == null || clientLevel == null) {
            return false;
        }

        ServerLevel serverLevel = replayServer.getLevel(clientLevel.dimension());
        if (serverLevel == null) {
            return false;
        }

        BlockEffectManager.OpacityRenderPlan renderPlan = replayServer.getBlockEffectManager().getOpacityRenderPlan(serverLevel);
        if (renderPlan.isEmpty()) {
            return false;
        }

        RenderTarget mainRenderTarget = minecraft.getMainRenderTarget();
        baseTarget = FramebufferUtils.resizeOrCreateFramebuffer(baseTarget, mainRenderTarget.width, mainRenderTarget.height);
        accumulationTargetA = FramebufferUtils.resizeOrCreateFramebuffer(accumulationTargetA, mainRenderTarget.width, mainRenderTarget.height);
        accumulationTargetB = FramebufferUtils.resizeOrCreateFramebuffer(accumulationTargetB, mainRenderTarget.width, mainRenderTarget.height);
        passTarget = FramebufferUtils.resizeOrCreateFramebuffer(passTarget, mainRenderTarget.width, mainRenderTarget.height);

        ClientOpacityMutationSession mutationSession = new ClientOpacityMutationSession(clientLevel, renderPlan);
        renderingMultipass = true;
        try {
            mutationSession.applyBase();
            renderWorldHookThisPass = true;
            forceSynchronousChunkRebuilds = true;
            levelRenderer.renderLevel(graphicsResourceAllocator, deltaTracker, renderBlockOutline, camera, frustumMatrix, projectionMatrix, projection, gpuBufferSlice, clearColour, drawBlockOutline);
            forceSynchronousChunkRebuilds = false;

            FramebufferUtils.blit(mainRenderTarget, baseTarget);
            FramebufferUtils.blit(mainRenderTarget, accumulationTargetA);

            renderWorldHookThisPass = false;
            for (BlockEffectManager.OpacityBucket bucket : renderPlan.buckets()) {
                mutationSession.showBucket(bucket);
                forceSynchronousChunkRebuilds = true;
                levelRenderer.renderLevel(graphicsResourceAllocator, deltaTracker, renderBlockOutline, camera, frustumMatrix, projectionMatrix, projection, gpuBufferSlice, clearColour, drawBlockOutline);
                forceSynchronousChunkRebuilds = false;

                FramebufferUtils.blit(mainRenderTarget, passTarget);
                FramebufferUtils.accumulate(accumulationTargetB, accumulationTargetA, baseTarget, passTarget, bucket.opacity());

                RenderTarget temp = accumulationTargetA;
                accumulationTargetA = accumulationTargetB;
                accumulationTargetB = temp;

                mutationSession.hideBucket(bucket);
            }

            mutationSession.restore();
            FramebufferUtils.blit(accumulationTargetA, mainRenderTarget);
            return true;
        } finally {
            if (mutationSession != null) {
                mutationSession.restore();
            }
            renderWorldHookThisPass = true;
            forceSynchronousChunkRebuilds = false;
            renderingMultipass = false;
        }
    }

    private static class ClientOpacityMutationSession {
        private static final BlockState AIR = Blocks.AIR.defaultBlockState();

        private final ClientLevel clientLevel;
        private final BlockEffectManager.OpacityRenderPlan renderPlan;
        private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        private ClientOpacityMutationSession(ClientLevel clientLevel, BlockEffectManager.OpacityRenderPlan renderPlan) {
            this.clientLevel = clientLevel;
            this.renderPlan = renderPlan;
        }

        private void applyBase() {
            this.applyState(this.renderPlan.allPositions(), AIR);
        }

        private void showBucket(BlockEffectManager.OpacityBucket bucket) {
            this.applyState(bucket.positions(), null);
        }

        private void hideBucket(BlockEffectManager.OpacityBucket bucket) {
            this.applyState(bucket.positions(), AIR);
        }

        private void restore() {
            this.applyState(this.renderPlan.allPositions(), null);
        }

        private void applyState(it.unimi.dsi.fastutil.longs.LongList positions, BlockState fixedState) {
            boolean changed = false;
            for (long position : positions) {
                int x = BlockPos.getX(position);
                int y = BlockPos.getY(position);
                int z = BlockPos.getZ(position);
                this.mutable.set(x, y, z);

                BlockState targetState = fixedState != null ? fixedState : this.renderPlan.getBlockState(position);
                if (targetState == null) {
                    continue;
                }

                LevelChunk chunk = this.clientLevel.getChunkAt(this.mutable);
                BlockState oldState = ((LevelChunkExt) chunk).flashback$setBlockStateWithoutUpdates(this.mutable, targetState);
                if (oldState == null) {
                    continue;
                }

                this.clientLevel.sendBlockUpdated(this.mutable, oldState, targetState, 3);
                changed = true;
            }

            if (changed) {
                Minecraft.getInstance().levelRenderer.needsUpdate();
            }
        }
    }
}
