package com.kuilunfuzhe.monvhua.network;

import com.kuilunfuzhe.monvhua.network.camerawatch.*;
import com.kuilunfuzhe.monvhua.network.action.ActionPackets;
import com.kuilunfuzhe.monvhua.network.bodypose.ApplySkeletalPoseC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket;
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
import com.kuilunfuzhe.monvhua.network.imitate.SilencePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWavePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWaveStartS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.UpdateImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorPackets;
import com.kuilunfuzhe.monvhua.network.openback.*;
import com.kuilunfuzhe.monvhua.network.secrecy.RequestSecrecyConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyConfigUpdateC2SPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyStateS2CPacket;

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
        FocusStatusPacket.register();
        ParticlePacket.register();
        StrengthPacket.register();
        CameraWatchBindS2CPacket.register();
        CameraWatchUnbindS2CPacket.register();
        CameraUpdateS2CPacket.register();
        MirrorPackets.registerS2C();
        SecrecyConfigS2CPacket.register();
        SecrecyStateS2CPacket.register();
        CarryPoseSyncS2CPacket.register();
        ActionPackets.registerS2C();
        ImitateOpenUIPacket.register();
        SoundWaveStartS2CPacket.register();
        ImitateConfigS2CPacket.register();
        ImitateSyncS2CPacket.register();
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
        CameraWatchStartC2SPacket.register();
        CameraWatchStopC2SPacket.register();
        MirrorPackets.registerC2S();
        RequestSecrecyConfigC2SPacket.register();
        SecrecyConfigUpdateC2SPacket.register();
        ActionPackets.registerC2S();
        FloatingPackets.registerC2S();
        GravityPackets.registerC2S();
        SoundWavePacket.register();
        RequestImitateConfigC2SPacket.register();
        UpdateImitateConfigC2SPacket.register();
        SilencePacket.register();
    }
}
