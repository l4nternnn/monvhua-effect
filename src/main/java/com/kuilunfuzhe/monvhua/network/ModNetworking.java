package com.kuilunfuzhe.monvhua.network;

import com.kuilunfuzhe.monvhua.network.camerawatch.*;
import com.kuilunfuzhe.monvhua.network.action.ActionPackets;
import com.kuilunfuzhe.monvhua.network.area_tip.AreaTipPackets;
import com.kuilunfuzhe.monvhua.network.bodypose.ApplySkeletalPoseC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlaceTrueSkeletalBodyC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.UpdateBodyPoseDefaultsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.UpdatePlacedBodyPoseC2SPacket;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryTransformPackets;
import com.kuilunfuzhe.monvhua.network.drawingboard.DrawingBoardPackets;
import com.kuilunfuzhe.monvhua.network.evil_eyes.EvilEyesPackets;
import com.kuilunfuzhe.monvhua.network.floating.FloatingEnergySyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.floating.FloatingPackets;
import com.kuilunfuzhe.monvhua.network.floating.FullWitchTagSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.gazeguidance.*;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateOpenUIPacket;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.RequestImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.imitate.RequestSilenceTargetsC2SPacket;
import com.kuilunfuzhe.monvhua.network.imitate.SilencePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SilenceTargetsS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWavePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWaveStartS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.UpdateImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets;
import com.kuilunfuzhe.monvhua.network.openback.*;
import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.network.plant.PlantMagicPackets;
import com.kuilunfuzhe.monvhua.network.secret.SecretPackets;
import com.kuilunfuzhe.monvhua.network.through.RequestThroughConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.through.ThroughConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.through.ThroughConfigUpdateC2SPacket;
import com.kuilunfuzhe.monvhua.network.through.ThroughStateS2CPacket;
import com.kuilunfuzhe.monvhua.network.fantasy.FantasyS2CPacket;

public class ModNetworking {

    public static void registerS2CPackets() {
        EvilEyesPackets.registerS2C();
        ToggleImagesS2CPacket.register();
        MarkParticleS2CPacket.register();
        SyncConfigS2CPacket.register();
        EnergySyncPacket.register();
        FloatingEnergySyncS2CPacket.register();
        FloatingPackets.registerS2C();
        FullWitchTagSyncS2CPacket.register();
        MarkCountPacket.register();
        MarkedListPacket.register();
        FocusStatusPacket.register();
        ParticlePacket.register();
        StrengthPacket.register();
        CameraWatchBindS2CPacket.register();
        CameraWatchUnbindS2CPacket.register();
        CameraUpdateS2CPacket.register();
        MirrorPackets.registerS2C();
        ThroughConfigS2CPacket.register();
        ThroughStateS2CPacket.register();
        CarryPoseSyncS2CPacket.register();
        CarryTransformPackets.registerS2C();
        ActionPackets.registerS2C();
        GravityPackets.registerS2C();
        PaintOverlayPackets.registerS2C();
        DrawingBoardPackets.registerS2C();
        PlantMagicPackets.registerS2C();
        AreaTipPackets.registerS2C();
        ImitateOpenUIPacket.register();
        SoundWaveStartS2CPacket.register();
        ImitateConfigS2CPacket.register();
        ImitateSyncS2CPacket.register();
        SilenceTargetsS2CPacket.register();
        SecretPackets.registerS2C();
        FantasyS2CPacket.register();

    }

    public static void registerC2SPackets() {
        EvilEyesPackets.registerC2S();
        MagicPacket.register();
        RightClickActionPacket.register();
        RequestConfigC2SPacket.register();
        UpdateConfigC2SPacket.register();
        OpenOtherInventoryPayload.register();
        CarryEntityPayload.register();
        PlaceCarriedEntityPayload.register();
        ApplySkeletalPoseC2SPacket.register();
        PlacePoseEditorItemsC2SPacket.register();
        PlacePosedBodyC2SPacket.register();
        PlaceTrueSkeletalBodyC2SPacket.register();
        UpdateBodyPoseDefaultsC2SPacket.register();
        UpdatePlacedBodyPoseC2SPacket.register();
        CarryTransformPackets.registerC2S();
        CameraWatchStartC2SPacket.register();
        CameraWatchStopC2SPacket.register();
        MirrorPackets.registerC2S();
        RequestThroughConfigC2SPacket.register();
        ThroughConfigUpdateC2SPacket.register();
        ActionPackets.registerC2S();
        FloatingPackets.registerC2S();
        GravityPackets.registerC2S();
        PaintOverlayPackets.registerC2S();
        DrawingBoardPackets.registerC2S();
        PlantMagicPackets.registerC2S();
        AreaTipPackets.registerC2S();
        SoundWavePacket.register();
        RequestImitateConfigC2SPacket.register();
        UpdateImitateConfigC2SPacket.register();
        SilencePacket.register();
        RequestSilenceTargetsC2SPacket.register();
        SecretPackets.registerC2S();
    }
}
