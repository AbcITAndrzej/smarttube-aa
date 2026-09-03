package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.MobileSettingsAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNativeDependencies;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNativeViewModelFactory;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host.MobileNativeActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileLoadState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSettingItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.startup.MobileStartupPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.update.MobileUpdateController;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobileSettingsViewModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileSettingsFragment extends Fragment {
    private static final int REQUEST_INSTALL_PERMISSION = 701;
    private static final String CUSTOM_PREFIX = "mobile-custom:";
    private static final String ID_ANDROID_AUTO = CUSTOM_PREFIX + "android-auto";
    private static final String ID_PLAYER = CUSTOM_PREFIX + "player";
    private static final String ID_RADIO = CUSTOM_PREFIX + "radio";
    private static final String ID_OFFLINE = CUSTOM_PREFIX + "offline";
    private static final String ID_DIAGNOSTICS = CUSTOM_PREFIX + "diagnostics";
    private static final String ID_CLASSIC = CUSTOM_PREFIX + "classic";
    private static final String ID_UPDATE = CUSTOM_PREFIX + "update";
    private static final String ID_DISABLE_STARTUP_UPDATE = CUSTOM_PREFIX + "disable-startup-update";

    private MobileUpdateController updateController;
    private MobileStartupPreferences startupPreferences;
    private MobileSettingsAdapter adapter;
    private List<MobileSettingItem> legacyItems = Collections.emptyList();

    public static MobileSettingsFragment newInstance() { return new MobileSettingsFragment(); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,
                                                 @Nullable ViewGroup c,
                                                 @Nullable Bundle s) {
        return i.inflate(R.layout.mobile_native_fragment_settings, c, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MobileSettingsViewModel vm = new ViewModelProvider(this,
                new MobileNativeViewModelFactory(MobileNativeDependencies.get(), getArguments()))
                .get(MobileSettingsViewModel.class);
        RecyclerView list = view.findViewById(R.id.mobile_list);
        TextView error = view.findViewById(R.id.mobile_error);
        ProgressBar progress = view.findViewById(R.id.mobile_progress);
        View retry = view.findViewById(R.id.mobile_retry_button);

        updateController = new MobileUpdateController(requireActivity(), this::requestInstallPermission);
        startupPreferences = new MobileStartupPreferences(requireContext());
        adapter = new MobileSettingsAdapter(item -> handleClick(vm, item));

        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(false);
        list.setAdapter(adapter);
        retry.setOnClickListener(v -> vm.load());

        vm.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == MobileLoadState.Status.LOADING && !value.hasData()
                    ? View.VISIBLE : View.GONE);
            error.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            retry.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            if (value.getError() != null) error.setText(value.getError().getMessage());
            if (value.getData() != null) {
                legacyItems = new ArrayList<>(value.getData());
                submitUnifiedSettings();
            }
        });
        if (vm.getState().getValue() == null
                || vm.getState().getValue().getStatus() == MobileLoadState.Status.IDLE) vm.load();
    }

    private void submitUnifiedSettings() {
        if (adapter == null || startupPreferences == null) return;
        List<MobileSettingItem> rows = new ArrayList<>();

        // SmartTube exposes Accounts as its first legacy category. Keep it above all mobile/AA rows.
        if (!legacyItems.isEmpty()) rows.add(legacyItems.get(0));

        rows.add(action(ID_ANDROID_AUTO, R.string.mobile_aa_settings_entry));
        rows.add(action(ID_PLAYER, R.string.mobile_player_settings_entry));
        rows.add(action(ID_RADIO, R.string.mobile_radio_settings_entry));
        rows.add(action(ID_OFFLINE, R.string.mobile_offline_entry));
        rows.add(action(ID_DIAGNOSTICS, R.string.mobile_diagnostics_entry));
        rows.add(action(ID_CLASSIC, R.string.mobile_native_classic));
        rows.add(action(ID_UPDATE, R.string.mobile_update_check));
        rows.add(new MobileSettingItem(ID_DISABLE_STARTUP_UPDATE, MobileSettingItem.Type.SWITCH,
                getString(R.string.mobile_update_disable_startup), "",
                Boolean.toString(startupPreferences.isStartupUpdateCheckDisabled()), true,
                Collections.emptyList()));

        // Remaining stock SmartTube categories follow in their original order.
        for (int i = 1; i < legacyItems.size(); i++) rows.add(legacyItems.get(i));
        adapter.submit(rows);
    }

    private MobileSettingItem action(String id, int titleRes) {
        return new MobileSettingItem(id, MobileSettingItem.Type.ACTION, getString(titleRes), "",
                "invoke", true, Collections.emptyList());
    }

    private void handleClick(MobileSettingsViewModel vm, MobileSettingItem item) {
        String id = item.getId();
        if (id != null && id.startsWith(CUSTOM_PREFIX)) {
            handleCustomClick(id, item);
            return;
        }

        if (item.getType() == MobileSettingItem.Type.SWITCH) {
            vm.update(item, Boolean.toString(!item.isChecked()));
        } else if (item.getType() == MobileSettingItem.Type.CHOICE && !item.getOptions().isEmpty()) {
            String[] options = item.getOptions().toArray(new String[0]);
            new AlertDialog.Builder(requireContext()).setTitle(item.getTitle())
                    .setItems(options, (dialog, which) -> vm.update(item, options[which])).show();
        } else if (item.getType() == MobileSettingItem.Type.ACTION) {
            vm.update(item, "invoke");
        }
    }

    private void handleCustomClick(String id, MobileSettingItem item) {
        switch (id) {
            case ID_ANDROID_AUTO:
                MobileFragmentSupport.navigator(this).openAndroidAutoSettings();
                break;
            case ID_PLAYER:
                MobileFragmentSupport.navigator(this).openPlayerSettings();
                break;
            case ID_RADIO:
                MobileFragmentSupport.navigator(this).openRadioSettings();
                break;
            case ID_OFFLINE:
                MobileFragmentSupport.navigator(this).openOfflineSettings();
                break;
            case ID_DIAGNOSTICS:
                MobileFragmentSupport.navigator(this).openDiagnostics();
                break;
            case ID_CLASSIC:
                showClassicConfirmation();
                break;
            case ID_UPDATE:
                if (updateController != null) updateController.check();
                break;
            case ID_DISABLE_STARTUP_UPDATE:
                if (startupPreferences != null) {
                    startupPreferences.setStartupUpdateCheckDisabled(!item.isChecked());
                    submitUnifiedSettings();
                }
                break;
            default:
                break;
        }
    }

    private void showClassicConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_native_classic_confirm_title)
                .setMessage(R.string.mobile_native_classic_confirm_message)
                .setNegativeButton(R.string.mobile_native_classic_no, null)
                .setPositiveButton(R.string.mobile_native_classic_yes, (dialog, which) -> {
                    if (requireActivity() instanceof MobileNativeActivity) {
                        ((MobileNativeActivity) requireActivity()).openClassicHome();
                    }
                })
                .show();
    }

    private void requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (updateController != null) updateController.resumeInstallAfterPermission();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + requireContext().getPackageName()));
        startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
    }

    @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL_PERMISSION && updateController != null) {
            updateController.resumeInstallAfterPermission();
        }
    }

    @Override public void onDestroyView() {
        if (updateController != null) {
            updateController.close();
            updateController = null;
        }
        adapter = null;
        startupPreferences = null;
        legacyItems = Collections.emptyList();
        super.onDestroyView();
    }
}
