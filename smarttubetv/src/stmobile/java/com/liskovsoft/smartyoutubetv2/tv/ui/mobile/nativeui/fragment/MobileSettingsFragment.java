package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.MobileSettingsAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobileSettingsViewModel;

public final class MobileSettingsFragment extends Fragment {
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
        MobileSettingsAdapter adapter = new MobileSettingsAdapter(item -> handleClick(vm, item));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(true);
        list.setAdapter(adapter);
        retry.setOnClickListener(v -> vm.load());
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == MobileLoadState.Status.LOADING && !value.hasData()
                    ? View.VISIBLE : View.GONE);
            error.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            retry.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            if (value.getError() != null) error.setText(value.getError().getMessage());
            if (value.getData() != null) adapter.submit(value.getData());
        });
        if (vm.getState().getValue() == null
                || vm.getState().getValue().getStatus() == MobileLoadState.Status.IDLE) vm.load();
    }

    private void handleClick(MobileSettingsViewModel vm, MobileSettingItem item) {
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
}
