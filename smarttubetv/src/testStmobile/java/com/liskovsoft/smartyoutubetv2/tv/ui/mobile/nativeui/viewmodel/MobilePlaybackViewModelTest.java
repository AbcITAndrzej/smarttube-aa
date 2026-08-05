package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileBackgroundPlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.Collections;
import org.junit.*;
import static org.junit.Assert.*;

public class MobilePlaybackViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Test public void toggleUsesSnapshotInsteadOfUiGuess() {
        FakePlayer player = new FakePlayer();
        MobilePlaybackViewModel vm = new MobilePlaybackViewModel(player, "abc", 0);
        player.listener.onPlaybackSnapshot(new MobilePlaybackSnapshot("abc", "Title", "", true, true,
                false, 0, 1000, 0, 1f, Collections.emptyList(), Collections.emptyList()));
        vm.togglePlayPause();
        assertEquals(1, player.pauseCalls);
    }
    @Test public void backgroundPauseResumesOnlyWhenAutomaticallyPaused() {
        FakePlayer player = new FakePlayer();
        MobilePlaybackViewModel vm = new MobilePlaybackViewModel(player, "abc", 0);
        player.listener.onPlaybackSnapshot(new MobilePlaybackSnapshot("abc", "Title", "", true, true,
                false, 0, 1000, 0, 1f, Collections.emptyList(), Collections.emptyList()));
        vm.onHostStop(false);
        assertEquals(1, player.pauseCalls);
        vm.onHostStart();
        assertEquals(1, player.playCalls);
        vm.onHostStart();
        assertEquals(1, player.playCalls);
    }

    @Test public void backgroundCapablePlayerIsNotPausedWhenScreenTurnsOff() {
        BackgroundPlayer player = new BackgroundPlayer();
        MobilePlaybackViewModel vm = new MobilePlaybackViewModel(player, "abc", 0);
        player.listener.onPlaybackSnapshot(new MobilePlaybackSnapshot("abc", "Title", "", true, true,
                false, 0, 1000, 0, 1f, Collections.emptyList(), Collections.emptyList()));
        vm.onHostStop(false);
        assertEquals(0, player.pauseCalls);
        assertFalse(player.hostVisible);
        vm.onHostStart();
        assertTrue(player.hostVisible);
        assertEquals(0, player.playCalls);
    }

    @Test public void releaseIsIdempotent() {
        FakePlayer player = new FakePlayer(); MobilePlaybackViewModel vm = new MobilePlaybackViewModel(player, "abc", 0);
        vm.release(); vm.release(); assertEquals(1, player.releaseCalls);
    }
    private static class FakePlayer implements MobilePlaybackRepository {
        Listener listener; int playCalls; int pauseCalls; int releaseCalls;
        @Override public void setListener(Listener listener) { this.listener = listener; }
        @Override public void prepare(String id, long pos) {}
        @Override public void play() { playCalls++; }
        @Override public void pause() { pauseCalls++; }
        @Override public void seekTo(long p) {}
        @Override public void seekBy(long d) {}
        @Override public void setPlaybackSpeed(float s) {}
        @Override public void selectAudioTrack(String id) {}
        @Override public void selectSubtitleTrack(String id) {}
        @Override public void release() { releaseCalls++; }
    }
    private static final class BackgroundPlayer extends FakePlayer
            implements MobileBackgroundPlaybackRepository {
        boolean hostVisible = true;
        @Override public void setHostVisible(boolean visible) { hostVisible = visible; }
        @Override public boolean isBackgroundPlaybackEnabled() { return true; }
    }
}
