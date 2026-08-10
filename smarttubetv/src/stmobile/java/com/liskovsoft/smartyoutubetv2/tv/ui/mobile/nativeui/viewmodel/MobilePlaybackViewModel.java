package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileBackgroundPlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.List;

public final class MobilePlaybackViewModel extends ViewModel {
    private final MobilePlaybackRepository repository;
    private final MutableLiveData<MobileLoadState<MobilePlaybackSnapshot>> state =
            new MutableLiveData<>(MobileLoadState.<MobilePlaybackSnapshot>idle());
    private boolean prepared;
    private boolean released;
    private boolean autoPaused;

    public MobilePlaybackViewModel(MobilePlaybackRepository repository, String mediaId,
                                   long startPositionMs) {
        this(repository, mediaId, startPositionMs, null);
    }

    public MobilePlaybackViewModel(MobilePlaybackRepository repository, String mediaId,
                                   long startPositionMs, List<String> playbackQueue) {
        this.repository = repository;
        repository.setListener(new MobilePlaybackRepository.Listener() {
            @Override public void onPlaybackSnapshot(MobilePlaybackSnapshot snapshot) {
                state.postValue(MobileLoadState.content(snapshot));
            }
            @Override public void onPlaybackError(MobileError error) {
                MobilePlaybackSnapshot previous = state.getValue() == null ? null : state.getValue().getData();
                state.postValue(MobileLoadState.error(previous, error));
            }
        });
        if (mediaId != null && !mediaId.isEmpty()) {
            repository.setPlaybackQueue(playbackQueue, mediaId);
            state.setValue(MobileLoadState.loading(null, false));
            repository.prepare(mediaId, Math.max(0, startPositionMs));
            prepared = true;
        }
    }

    public LiveData<MobileLoadState<MobilePlaybackSnapshot>> getState() { return state; }
    public MobilePlaybackRepository getRepository() { return repository; }

    public void togglePlayPause() {
        MobilePlaybackSnapshot snapshot = state.getValue() == null ? null : state.getValue().getData();
        if (snapshot != null && snapshot.isPlaying()) repository.pause(); else repository.play();
    }

    public void play() { repository.play(); }
    public void pause() { repository.pause(); }
    public void playNext() { repository.playNext(); }
    public void playPrevious() { repository.playPrevious(); }

    public void seekBy(long deltaMs) { repository.seekBy(deltaMs); }
    public void seekTo(long positionMs) { repository.seekTo(Math.max(0, positionMs)); }
    public void selectVideoTrack(String id) { repository.selectVideoTrack(id); }
    public void selectAudioTrack(String id) { repository.selectAudioTrack(id); }
    public void selectSubtitleTrack(String id) { repository.selectSubtitleTrack(id); }
    public void setSpeed(float speed) { repository.setPlaybackSpeed(speed); }
    public void setResizeMode(int mode) { repository.setResizeMode(mode); }

    public void onHostStart() {
        if (repository instanceof MobileBackgroundPlaybackRepository) {
            ((MobileBackgroundPlaybackRepository) repository).setHostVisible(true);
            autoPaused = false;
            return;
        }
        if (autoPaused && !released) {
            autoPaused = false;
            repository.play();
        }
    }

    public void onHostStop(boolean keepPlayerAlive) {
        if (repository instanceof MobileBackgroundPlaybackRepository
                && ((MobileBackgroundPlaybackRepository) repository).isBackgroundPlaybackEnabled()) {
            ((MobileBackgroundPlaybackRepository) repository).setHostVisible(false);
            return;
        }
        MobilePlaybackSnapshot current = state.getValue() == null ? null : state.getValue().getData();
        if (!keepPlayerAlive && current != null && current.isPlaying()) {
            autoPaused = true;
            repository.pause();
        }
    }

    public boolean isPrepared() { return prepared; }

    public void release() {
        if (released) return;
        released = true;
        repository.setListener(null);
        repository.release();
    }

    @Override protected void onCleared() { release(); }
}
