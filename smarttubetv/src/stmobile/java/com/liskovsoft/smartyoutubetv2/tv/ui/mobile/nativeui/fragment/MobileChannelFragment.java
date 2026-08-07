package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.os.Bundle;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.MobileMediaAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobileChannelViewModel;

public final class MobileChannelFragment extends Fragment {
    public static MobileChannelFragment newInstance(String channelId) {
        MobileChannelFragment fragment = new MobileChannelFragment();
        Bundle args = new Bundle();
        args.putString("channel_id", channelId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_native_fragment_channel, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MobileChannelViewModel vm = new ViewModelProvider(this,
                new MobileNativeViewModelFactory(MobileNativeDependencies.get(), getArguments()))
                .get(MobileChannelViewModel.class);
        RecyclerView list = view.findViewById(R.id.mobile_list);
        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        TextView title = view.findViewById(R.id.mobile_channel_title);
        TextView description = view.findViewById(R.id.mobile_channel_description);
        TextView subscribers = view.findViewById(R.id.mobile_channel_subscribers);
        TextView error = view.findViewById(R.id.mobile_error);
        ProgressBar progress = view.findViewById(R.id.mobile_progress);
        View retry = view.findViewById(R.id.mobile_retry_button);
        final MobileMediaAdapter[] adapterRef = new MobileMediaAdapter[1];
        MobileMediaAdapter adapter = new MobileMediaAdapter(MobileNativeDependencies.get().imageLoader(), item -> {
            if (item.getKind() == MobileMediaItem.Kind.PLAYLIST
                    || item.getKind() == MobileMediaItem.Kind.SECTION_LINK) {
                MobileFragmentSupport.navigator(this).openBrowseItem(item.getId());
            } else if (item.isPlayable()) {
                if (item.getKind() == MobileMediaItem.Kind.SHORT) {
                    MobileFragmentSupport.navigator(this).openShortPlayback(
                            item.getId(), item.getProgressMs(),
                            adapterRef[0].getPlayableIds(MobileMediaItem.Kind.SHORT));
                } else {
                    MobileFragmentSupport.navigator(this).openPlayback(
                            item.getId(), item.getProgressMs());
                }
            }
        });
        adapterRef[0] = adapter;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager grid = new GridLayoutManager(requireContext(), 2);
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int position) {
                    return adapter.getLandscapeSpanSize(position);
                }
            });
            list.setLayoutManager(grid);
        } else {
            list.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        list.setHasFixedSize(true);
        list.setAdapter(adapter);
        toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());
        retry.setOnClickListener(v -> vm.load());
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == MobileLoadState.Status.LOADING && !value.hasData()
                    ? View.VISIBLE : View.GONE);
            error.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            retry.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            if (value.getError() != null) error.setText(value.getError().getMessage());
            MobileChannelPayload payload = value.getData();
            if (payload != null) {
                toolbar.setTitle(payload.getTitle());
                title.setText(payload.getTitle());
                description.setText(payload.getDescription());
                subscribers.setText(payload.getSubscriberText());
                adapter.submitSections(payload.getSections());
            }
        });
        if (vm.getState().getValue() == null
                || vm.getState().getValue().getStatus() == MobileLoadState.Status.IDLE) vm.load();
    }
}
