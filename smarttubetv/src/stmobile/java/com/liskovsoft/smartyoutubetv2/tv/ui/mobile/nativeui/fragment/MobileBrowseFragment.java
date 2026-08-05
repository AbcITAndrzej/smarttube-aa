package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.os.Bundle;
import android.view.*;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.MobileMediaAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobileBrowseViewModel;

public final class MobileBrowseFragment extends Fragment {
    public static MobileBrowseFragment newInstance(String pageId) {
        MobileBrowseFragment fragment = new MobileBrowseFragment();
        Bundle args = new Bundle();
        args.putString("page_id", pageId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_native_fragment_browse, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MobileBrowseViewModel vm = new ViewModelProvider(this,
                new MobileNativeViewModelFactory(MobileNativeDependencies.get(), getArguments()))
                .get(MobileBrowseViewModel.class);
        RecyclerView list = view.findViewById(R.id.mobile_list);
        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        TextView error = view.findViewById(R.id.mobile_error);
        ProgressBar progress = view.findViewById(R.id.mobile_progress);
        View retry = view.findViewById(R.id.mobile_retry_button);
        MobileMediaAdapter adapter = new MobileMediaAdapter(MobileNativeDependencies.get().imageLoader(), item -> {
            if (item.getKind() == MobileMediaItem.Kind.CHANNEL) {
                MobileFragmentSupport.navigator(this).openChannel(item.getId());
            } else if (item.isPlayable()) {
                MobileFragmentSupport.navigator(this).openPlayback(item.getId(), item.getProgressMs());
            }
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(true);
        list.setAdapter(adapter);
        retry.setOnClickListener(v -> vm.refresh());
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == MobileLoadState.Status.LOADING && !value.hasData()
                    ? View.VISIBLE : View.GONE);
            error.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            retry.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            if (value.getError() != null) error.setText(value.getError().getMessage());
            if (value.getData() != null) {
                toolbar.setTitle(value.getData().getTitle());
                adapter.submitSections(value.getData().getSections());
            }
        });
        if (vm.getState().getValue() == null
                || vm.getState().getValue().getStatus() == MobileLoadState.Status.IDLE) vm.load();
    }
}
