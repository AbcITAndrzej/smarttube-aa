package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive.AndroidAutoPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive.ExperimentalCarVideoGate;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive.SmartTubeAutoMusicService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileBrowseRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileRequest;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileResultCallback;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNativeDependencies;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileBrowsePayload;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phone-side configuration for SmartTube's Android Auto media browser. */
public final class AndroidAutoSettingsFragment extends Fragment {
    private static final String ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead";
    private static final String ACTION_ANDROID_AUTO_SETTINGS =
            "com.google.android.projection.gearhead.SETTINGS";
    private static final String ACTION_ANDROID_AUTO_SETTINGS_FALLBACK =
            "android.settings.ANDROID_AUTO_SETTINGS";

    private final List<PlaylistEntry> sourcePlaylists = new ArrayList<>();
    private final List<PlaylistEntry> playlists = new ArrayList<>();
    private AndroidAutoPreferences preferences;
    private MobileBrowseRepository browseRepository;
    private MobileRequest playlistRequest = MobileRequest.NONE;
    private LinearLayout playlistContainer;
    private ProgressBar playlistProgress;
    private TextView playlistMessage;
    private TextView accessStatus;
    private SwitchMaterial accessEnabled;
    private SwitchMaterial developerConfirmed;
    private SwitchMaterial unknownSourcesConfirmed;
    private SwitchMaterial experimentalParkedVideo;
    private SwitchMaterial offlineLibrary;
    private SwitchMaterial offlineAutoFallback;
    private boolean bindingAccessSwitch;

    public static AndroidAutoSettingsFragment newInstance() {
        return new AndroidAutoSettingsFragment();
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_android_auto_settings_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        preferences = new AndroidAutoPreferences(requireContext());
        browseRepository = MobileNativeDependencies.get().browseRepository();
        playlistContainer = view.findViewById(R.id.mobile_aa_playlist_container);
        playlistProgress = view.findViewById(R.id.mobile_aa_playlist_progress);
        playlistMessage = view.findViewById(R.id.mobile_aa_playlist_message);
        accessStatus = view.findViewById(R.id.mobile_aa_access_status);
        accessEnabled = view.findViewById(R.id.mobile_aa_access_enabled);
        developerConfirmed = view.findViewById(R.id.mobile_aa_developer_confirmed);
        unknownSourcesConfirmed = view.findViewById(R.id.mobile_aa_unknown_sources_confirmed);
        experimentalParkedVideo = view.findViewById(R.id.mobile_aa_experimental_video);
        offlineLibrary = view.findViewById(R.id.mobile_aa_offline_library);
        offlineAutoFallback = view.findViewById(R.id.mobile_aa_offline_auto_fallback);

        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        toolbar.setNavigationIcon(R.drawable.mobile_ic_back_24);
        toolbar.setNavigationContentDescription(R.string.mobile_native_back);
        toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());

        bindAccessControls(view);
        bindOfflinePlayback();
        bindExperimentalVideo();
        view.findViewById(R.id.mobile_aa_playlist_refresh).setOnClickListener(v -> loadPlaylists(true));
        view.findViewById(R.id.mobile_aa_playlist_reset).setOnClickListener(v -> resetPlaylistLayout());
        loadPlaylists(false);
    }

    @Override public void onResume() {
        super.onResume();
        if (preferences != null) updateAccessStatus();
    }

    @Override public void onDestroyView() {
        if (playlistRequest != null) playlistRequest.cancel();
        playlistRequest = MobileRequest.NONE;
        playlistContainer = null;
        super.onDestroyView();
    }

    private void bindAccessControls(View view) {
        bindingAccessSwitch = true;
        accessEnabled.setChecked(isServiceEnabled());
        bindingAccessSwitch = false;
        developerConfirmed.setChecked(preferences.isDeveloperModeConfirmed());
        unknownSourcesConfirmed.setChecked(preferences.isUnknownSourcesConfirmed());

        accessEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (bindingAccessSwitch) return;
            if (!checked) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.mobile_aa_disable_title)
                        .setMessage(R.string.mobile_aa_disable_message)
                        .setPositiveButton(R.string.mobile_aa_disable_confirm, (dialog, which) -> {
                            setServiceEnabled(false);
                            updateAccessStatus();
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> setAccessSwitch(true))
                        .setOnCancelListener(dialog -> setAccessSwitch(true))
                        .show();
            } else {
                setServiceEnabled(true);
                updateAccessStatus();
            }
        });
        developerConfirmed.setOnCheckedChangeListener((button, checked) -> {
            preferences.setDeveloperModeConfirmed(checked);
            updateAccessStatus();
        });
        unknownSourcesConfirmed.setOnCheckedChangeListener((button, checked) -> {
            preferences.setUnknownSourcesConfirmed(checked);
            updateAccessStatus();
        });
        view.findViewById(R.id.mobile_aa_open_settings).setOnClickListener(v -> openAndroidAutoSettings());
        view.findViewById(R.id.mobile_aa_check_button).setOnClickListener(v -> checkAndEnableAccess());
        view.findViewById(R.id.mobile_aa_guide_button).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.mobile_aa_guide_title)
                        .setMessage(R.string.mobile_aa_guide_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
        updateAccessStatus();
    }

    private void bindOfflinePlayback() {
        offlineLibrary.setChecked(preferences.isOfflineLibraryEnabled());
        offlineAutoFallback.setChecked(preferences.isOfflineAutoFallbackEnabled());
        offlineAutoFallback.setEnabled(offlineLibrary.isChecked());

        offlineLibrary.setOnCheckedChangeListener((button, checked) -> {
            preferences.setOfflineLibraryEnabled(checked);
            offlineAutoFallback.setEnabled(checked);
        });
        offlineAutoFallback.setOnCheckedChangeListener((button, checked) ->
                preferences.setOfflineAutoFallbackEnabled(checked));
    }

    private void bindExperimentalVideo() {
        // Keep manifest component state synchronized after app updates/reinstalls.
        boolean enabled = preferences.isExperimentalParkedVideoEnabled();
        ExperimentalCarVideoGate.setEnabled(requireContext(), enabled);
        experimentalParkedVideo.setChecked(enabled);
        experimentalParkedVideo.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                preferences.setExperimentalParkedVideoEnabled(false);
                ExperimentalCarVideoGate.setEnabled(requireContext(), false);
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.mobile_aa_video_confirm_title)
                    .setMessage(R.string.mobile_aa_video_confirm_message)
                    .setPositiveButton(R.string.mobile_aa_video_confirm_enable, (dialog, which) -> {
                        preferences.setExperimentalParkedVideoEnabled(true);
                        ExperimentalCarVideoGate.setEnabled(requireContext(), true);
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        preferences.setExperimentalParkedVideoEnabled(false);
                        ExperimentalCarVideoGate.setEnabled(requireContext(), false);
                        experimentalParkedVideo.setChecked(false);
                    })
                    .setOnCancelListener(dialog -> {
                        preferences.setExperimentalParkedVideoEnabled(false);
                        ExperimentalCarVideoGate.setEnabled(requireContext(), false);
                        experimentalParkedVideo.setChecked(false);
                    })
                    .show();
        });
    }

    private void setAccessSwitch(boolean checked) {
        bindingAccessSwitch = true;
        accessEnabled.setChecked(checked);
        bindingAccessSwitch = false;
    }

    private void checkAndEnableAccess() {
        setServiceEnabled(true);
        setAccessSwitch(true);
        updateAccessStatus();
        boolean ready = isAndroidAutoInstalled() && isServiceDeclared() && isServiceEnabled()
                && preferences.isDeveloperModeConfirmed()
                && (!isManualInstall() || preferences.isUnknownSourcesConfirmed());
        new AlertDialog.Builder(requireContext())
                .setTitle(ready ? R.string.mobile_aa_ready_title : R.string.mobile_aa_not_ready_title)
                .setMessage(ready ? R.string.mobile_aa_ready_message : R.string.mobile_aa_not_ready_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void updateAccessStatus() {
        if (accessStatus == null) return;
        boolean autoInstalled = isAndroidAutoInstalled();
        boolean serviceReady = isServiceDeclared() && isServiceEnabled();
        boolean manual = isManualInstall();
        boolean stepsReady = preferences.isDeveloperModeConfirmed()
                && (!manual || preferences.isUnknownSourcesConfirmed());
        boolean ready = autoInstalled && serviceReady && stepsReady;
        accessStatus.setText(getString(R.string.mobile_aa_status_format,
                yesNo(autoInstalled), yesNo(serviceReady),
                manual ? getString(R.string.mobile_aa_install_manual)
                        : getString(R.string.mobile_aa_install_trusted),
                yesNo(stepsReady),
                ready ? getString(R.string.mobile_aa_status_ready)
                        : getString(R.string.mobile_aa_status_action_required)));
    }

    private String yesNo(boolean value) {
        return getString(value ? R.string.mobile_aa_yes : R.string.mobile_aa_no);
    }

    private boolean isAndroidAutoInstalled() {
        try {
            ApplicationInfo info = requireContext().getPackageManager()
                    .getApplicationInfo(ANDROID_AUTO_PACKAGE, 0);
            return info.enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean isServiceDeclared() {
        try {
            requireContext().getPackageManager().getServiceInfo(serviceComponent(), 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean isServiceEnabled() {
        int state = requireContext().getPackageManager()
                .getComponentEnabledSetting(serviceComponent());
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
    }

    private void setServiceEnabled(boolean enabled) {
        requireContext().getPackageManager().setComponentEnabledSetting(
                serviceComponent(),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private ComponentName serviceComponent() {
        return new ComponentName(requireContext(), SmartTubeAutoMusicService.class);
    }

    @SuppressWarnings("deprecation")
    private boolean isManualInstall() {
        try {
            String installer;
            if (Build.VERSION.SDK_INT >= 30) {
                installer = requireContext().getPackageManager()
                        .getInstallSourceInfo(requireContext().getPackageName())
                        .getInstallingPackageName();
            } else {
                installer = requireContext().getPackageManager()
                        .getInstallerPackageName(requireContext().getPackageName());
            }
            return !"com.android.vending".equals(installer);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void openAndroidAutoSettings() {
        try {
            startActivity(new Intent(ACTION_ANDROID_AUTO_SETTINGS)
                    .setPackage(ANDROID_AUTO_PACKAGE));
        } catch (Throwable firstError) {
            try {
                startActivity(new Intent(ACTION_ANDROID_AUTO_SETTINGS_FALLBACK));
            } catch (Throwable secondError) {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + ANDROID_AUTO_PACKAGE)));
                } catch (Throwable thirdError) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            }
        }
    }

    private void loadPlaylists(boolean forceRefresh) {
        if (playlistRequest != null) playlistRequest.cancel();
        if (forceRefresh) browseRepository.invalidateBrowse("playlists");
        playlistProgress.setVisibility(View.VISIBLE);
        playlistMessage.setVisibility(View.GONE);
        playlistRequest = browseRepository.loadBrowse("playlists",
                new MobileResultCallback<MobileBrowsePayload>() {
                    @Override public void onSuccess(MobileBrowsePayload payload) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> showPlaylists(payload));
                    }

                    @Override public void onError(MobileError error) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> showPlaylistError(error));
                    }
                });
    }

    private void showPlaylists(MobileBrowsePayload payload) {
        sourcePlaylists.clear();
        Map<String, PlaylistEntry> unique = new LinkedHashMap<>();
        if (payload != null && payload.getSections() != null) {
            for (MobileSection section : payload.getSections()) {
                if (section == null || section.getItems() == null) continue;
                for (MobileMediaItem item : section.getItems()) {
                    if (item == null || item.getKind() != MobileMediaItem.Kind.PLAYLIST) continue;
                    String key = AndroidAutoPreferences.playlistKey(item);
                    if (key.isEmpty() || unique.containsKey(key)) continue;
                    unique.put(key, new PlaylistEntry(key, item.getTitle(), item.getSubtitle(), true));
                }
            }
        }
        sourcePlaylists.addAll(unique.values());
        rebuildConfiguredPlaylists();
        playlistProgress.setVisibility(View.GONE);
        playlistMessage.setVisibility(playlists.isEmpty() ? View.VISIBLE : View.GONE);
        playlistMessage.setText(R.string.mobile_aa_playlist_empty);
        renderPlaylists();
    }

    private void showPlaylistError(MobileError error) {
        playlistProgress.setVisibility(View.GONE);
        playlistMessage.setVisibility(View.VISIBLE);
        playlistMessage.setText(error == null || error.getMessage() == null
                ? getString(R.string.mobile_aa_playlist_error) : error.getMessage());
    }

    private void rebuildConfiguredPlaylists() {
        playlists.clear();
        Map<String, PlaylistEntry> byKey = new LinkedHashMap<>();
        for (PlaylistEntry entry : sourcePlaylists) byKey.put(entry.key, entry.copy());
        Set<String> hidden = preferences.getHiddenPlaylists();
        for (String key : preferences.orderAvailableKeys(byKey.keySet())) {
            PlaylistEntry entry = byKey.get(key);
            if (entry == null) continue;
            entry.visible = !hidden.contains(key);
            playlists.add(entry);
        }
    }

    private void renderPlaylists() {
        if (playlistContainer == null) return;
        playlistContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int index = 0; index < playlists.size(); index++) {
            PlaylistEntry entry = playlists.get(index);
            View row = inflater.inflate(R.layout.mobile_android_auto_playlist_item,
                    playlistContainer, false);
            TextView title = row.findViewById(R.id.mobile_aa_playlist_title);
            TextView summary = row.findViewById(R.id.mobile_aa_playlist_summary);
            SwitchMaterial visible = row.findViewById(R.id.mobile_aa_playlist_visible);
            MaterialButton moveUp = row.findViewById(R.id.mobile_aa_playlist_up);
            MaterialButton moveDown = row.findViewById(R.id.mobile_aa_playlist_down);
            title.setText(getString(R.string.mobile_aa_playlist_position, index + 1, entry.title));
            summary.setText(entry.summary);
            summary.setVisibility(entry.summary.isEmpty() ? View.GONE : View.VISIBLE);
            visible.setChecked(entry.visible);
            row.setAlpha(entry.visible ? 1f : 0.55f);
            final int position = index;
            visible.setOnCheckedChangeListener((button, checked) -> {
                entry.visible = checked;
                row.setAlpha(checked ? 1f : 0.55f);
                savePlaylistLayout();
            });
            moveUp.setEnabled(index > 0);
            moveDown.setEnabled(index < playlists.size() - 1);
            moveUp.setOnClickListener(v -> movePlaylist(position, -1));
            moveDown.setOnClickListener(v -> movePlaylist(position, 1));
            playlistContainer.addView(row);
        }
    }

    private void movePlaylist(int position, int delta) {
        int target = position + delta;
        if (position < 0 || position >= playlists.size()
                || target < 0 || target >= playlists.size()) return;
        Collections.swap(playlists, position, target);
        savePlaylistLayout();
        renderPlaylists();
    }

    private void savePlaylistLayout() {
        List<String> order = new ArrayList<>();
        Set<String> hidden = new LinkedHashSet<>();
        for (PlaylistEntry entry : playlists) {
            order.add(entry.key);
            if (!entry.visible) hidden.add(entry.key);
        }
        preferences.savePlaylistLayout(order, hidden);
    }

    private void resetPlaylistLayout() {
        preferences.clearPlaylistLayout();
        rebuildConfiguredPlaylists();
        renderPlaylists();
    }

    private static final class PlaylistEntry {
        final String key;
        final String title;
        final String summary;
        boolean visible;

        PlaylistEntry(String key, String title, String summary, boolean visible) {
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.summary = summary == null ? "" : summary;
            this.visible = visible;
        }

        PlaylistEntry copy() {
            return new PlaylistEntry(key, title, summary, visible);
        }
    }
}
