package com.kuilunfuzhe.monvhua.network;

import com.kuilunfuzhe.monvhua.network.camerawatch.*;
import com.kuilunfuzhe.monvhua.network.action.*;
import com.kuilunfuzhe.monvhua.network.bodypose.ApplySkeletalPoseC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePoseEditorItemsC2SPacket;
import com.kuilunfuzhe.monvhua.network.bodypose.PlacePosedBodyC2SPacket;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryPoseSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.evil_eyes.*;
import com.kuilunfuzhe.monvhua.network.floating.FloatingEnergySyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.floating.FullWitchTagSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.gazeguidance.*;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateOpenUIPacket;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.RequestImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.imitate.SilencePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWavePacket;
import com.kuilunfuzhe.monvhua.network.imitate.SoundWaveStartS2CPacket;
import com.kuilunfuzhe.monvhua.network.imitate.UpdateImitateConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorChargeC2SPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorChargeSyncS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorStateS2CPacket;
import com.kuilunfuzhe.monvhua.network.mirror.MirrorToggleC2SPacket;
import com.kuilunfuzhe.monvhua.network.openback.*;
import com.kuilunfuzhe.monvhua.network.secrecy.RequestSecrecyConfigC2SPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyConfigS2CPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyConfigUpdateC2SPacket;
import com.kuilunfuzhe.monvhua.network.secrecy.SecrecyStateS2CPacket;

public class ModNetworking {

    public static void registerS2CPackets() {
        GlobalConfigS2CPacket.register();
        OpenUIPacket.register();
        EntityMarkedPayload.register();
        ToggleImagesS2CPacket.register();
        ForceExitViewPayload.register();
        MarkParticleS2CPacket.register();
        SyncConfigS2CPacket.register();
        EnergySyncPacket.register();
        FloatingEnergySyncS2CPacket.register();
        FullWitchTagSyncS2CPacket.register();
        MarkCountPacket.register();
        FocusStatusPacket.register();
        ParticlePacket.register();
        StrengthPacket.register();
        AnchorParticleS2CPacket.register();
        PlayerStageS2CPacket.register();
        ExplosionParticleS2CPacket.register();
        CameraWatchBindS2CPacket.register();
        CameraWatchUnbindS2CPacket.register();
        CameraUpdateS2CPacket.register();
        MirrorStateS2CPacket.register();
        MirrorConfigS2CPacket.register();
        MirrorChargeSyncS2CPacket.register();
        SecrecyConfigS2CPacket.register();
        SecrecyStateS2CPacket.register();
        CarryPoseSyncS2CPacket.register();
        ActionsConfigS2CPacket.register();
        ActionFilesListS2CPacket.register();
        PreviewResultS2CPacket.register();
        PreviewTimelineResultS2CPacket.register();
        TimelineStateS2CPacket.register();
        ImitateOpenUIPacket.register();
        SoundWaveStartS2CPacket.register();
        ImitateConfigS2CPacket.register();
        ImitateSyncS2CPacket.register();
    }

    public static void registerC2SPackets() {
        MarkEntityPayload.register();
        UnmarkEntityPayload.register();
        SelectViewPayload.register();
        ExitViewPayload.register();
        MagicPacket.register();
        RightClickActionPacket.register();
        RequestConfigC2SPacket.register();
        UpdateConfigC2SPacket.register();
        RequestGlobalConfigC2SPacket.register();
        UpdateGlobalConfigC2SPacket.register();
        PlaceParrotC2SPacket.register();
        AnchorDestroyC2SPacket.register();
        OpenOtherInventoryPayload.register();
        CarryEntityPayload.register();
        PlaceCarriedEntityPayload.register();
        ApplySkeletalPoseC2SPacket.register();
        PlacePoseEditorItemsC2SPacket.register();
        PlacePosedBodyC2SPacket.register();
        CameraWatchStartC2SPacket.register();
        CameraWatchStopC2SPacket.register();
        MirrorToggleC2SPacket.register();
        MirrorChargeC2SPacket.register();
        RequestSecrecyConfigC2SPacket.register();
        SecrecyConfigUpdateC2SPacket.register();
        RequestActionsConfigC2SPacket.register();
        UpdateActionsConfigC2SPacket.register();
        PreviewActionC2SPacket.register();
        PreviewTimelineC2SPacket.register();
        TimelineControlC2SPacket.register();
        ListActionFilesC2SPacket.register();
        LoadActionFileC2SPacket.register();
        SoundWavePacket.register();
        RequestImitateConfigC2SPacket.register();
        UpdateImitateConfigC2SPacket.register();
        SilencePacket.register();
    }
}