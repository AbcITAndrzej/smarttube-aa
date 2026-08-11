package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.os.Bundle;
import android.view.*;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.MobileMediaAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistDownloadService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.performance.MobilePerformanceMonitor;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRecord;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobileBrowseViewModel;
import java.util.ArrayList;
import java.util.List;

public final class MobileBrowseFragment extends Fragment {
    private static final String ARG_PAGE_ID = "page_id";
    private static final String ARG_ITEM_ID = "item_id";

    public static MobileBrowseFragment newInstance(String pageId) {
        MobileBrowseFragment fragment = new MobileBrowseFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PAGE_ID, pageId);
        fragment.setArguments(args);
        return fragment;
    }

    public static MobileBrowseFragment newItemInstance(String itemId) {
        MobileBrowseFragment fragment = new MobileBrowseFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ITEM_ID, itemId);
        fragment.setArguments(args);
        return fragment;
    }

    public String getPageId() {
        return getArguments() == null ? "home" : getArguments().getString(ARG_PAGE_ID, "home");
    }

    public boolean isItemDetail() {
        return getArguments() != null && !getArguments().getString(ARG_ITEM_ID, "").isEmpty();
    }

    public String getItemId() {
        return getArguments() == null ? "" : getArguments().getString(ARG_ITEM_ID, "");
    }

    private boolean isPlaylistDetail() {
        return isItemDetail() && getItemId().startsWith("playlist:");
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
        MobilePerformanceMonitor performance = MobilePerformanceMonitor.get(requireContext());
        RecyclerView list = view.findViewById(R.id.mobile_list);
        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        ChipGroup categories = view.findViewById(R.id.mobile_category_chips);
        View categoryScroll = view.findViewById(R.id.mobile_category_scroll);
        TextView error = view.findViewById(R.id.mobile_error);
        ProgressBar progress = view.findViewById(R.id.mobile_progress);
        View retry = view.findViewById(R.id.mobile_retry_button);
        final MobileMediaAdapter[] adapterRef = new MobileMediaAdapter[1];
        MobileMediaAdapter adapter = new MobileMediaAdapter(MobileNativeDependencies.get().imageLoader(), item -> {
            if (item.getKind() == MobileMediaItem.Kind.CHANNEL) {
                MobileFragmentSupport.navigator(this).openChannel(item.getId());
            } else if (item.getKind() == MobileMediaItem.Kind.PLAYLIST
                    || item.getKind() == MobileMediaItem.Kind.SECTION_LINK) {
                MobileFragmentSupport.navigator(this).openBrowseItem(item.getId());
            } else if (item.isPlayable()) {
                if (item.getKind() == MobileMediaItem.Kind.SHORT) {
                    MobileFragmentSupport.navigator(this).openShortPlayback(
                            item.getId(), item.getProgressMs(),
                            adapterRef[0].getPlayableIds(MobileMediaItem.Kind.SHORT));
                } else {
                    MobileFragmentSupport.navigator(this).openPlaybackQueue(
                            item.getId(), item.getProgressMs(),
                            adapterRef[0].getRegularPlayableIds());
                }
            }
        });
        adapterRef[0] = adapter;
        GridLayoutManager grid = new GridLayoutManager(requireContext(), 2);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.getGridSpanSize(position);
            }
        });
        list.setLayoutManager(grid);
        list.setHasFixedSize(false);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView,
                                             int dx, int dy) {
                if (dy <= 0) return;
                RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
                int last = manager instanceof GridLayoutManager
                        ? ((GridLayoutManager) manager).findLastVisibleItemPosition() : -1;
                int trigger = "shorts".equals(getPageId())
                        ? Math.max(1, adapter.getItemCount() * 3 / 4)
                        : adapter.getItemCount() - 6;
                if (last >= trigger) vm.loadMore();
            }
        });
        boolean showCategories = !isItemDetail() && isCategoryPage(getPageId());
        if (isItemDetail()) {
            categoryScroll.setVisibility(View.GONE);
            toolbar.setNavigationIcon(R.drawable.mobile_ic_back_24);
            toolbar.setNavigationContentDescription(R.string.mobile_native_back);
            toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());
            toolbar.getMenu().clear();
        } else {
            categoryScroll.setVisibility(showCategories ? View.VISIBLE : View.GONE);
            if (showCategories) setupCategories(categories, getPageId());
            int titleRes = titleForPage(getPageId());
            if (!showCategories && titleRes != 0) toolbar.setTitle(titleRes);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.mobile_action_search) {
                    MobileFragmentSupport.navigator(this).openSearch("");
                    return true;
                }
                if (item.getItemId() == R.id.mobile_action_notifications) {
                    MobileFragmentSupport.navigator(this).openBrowse("notifications");
                    return true;
                }
                return false;
            });
        }
        retry.setOnClickListener(v -> vm.refresh());
        final MobileBrowsePayload[] lastRenderedPayload = { null };
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == MobileLoadState.Status.LOADING && !value.hasData()
                    ? View.VISIBLE : View.GONE);
            error.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            retry.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            if (value.getError() != null) error.setText(value.getError().getMessage());
            if (value.getData() != null && value.getData() != lastRenderedPayload[0]) {
                lastRenderedPayload[0] = value.getData();
                if (isItemDetail()) toolbar.setTitle(value.getData().getTitle());
                if (isPlaylistDetail()) configurePlaylistOfflineAction(toolbar, value.getData());
                long renderStarted = performance.beginTrace("ST:BrowseRender");
                adapter.submitSections(localizeTopLevelSections(value.getData().getSections(), showCategories));
                performance.endBrowseTrace(renderStarted, isItemDetail() ? "detail" : getPageId(),
                        adapter.getItemCount());
                if (!isItemDetail() && "home".equals(getPageId())) {
                    performance.onHomeContentReady(requireActivity(), adapter.getItemCount());
                }
                if (value.getData().hasMore()) {
                    // Large tablets can display the whole first Shorts page without producing
                    // a scroll event. Fill one more page until the list is actually scrollable.
                    list.post(() -> {
                        if (isAdded() && !list.canScrollVertically(1)) vm.loadMore();
                    });
                }
            }
        });
        ProgressBar loadMore = view.findViewById(R.id.mobile_load_more);
        vm.getLoadingMore().observe(getViewLifecycleOwner(), loading ->
                loadMore.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        if (vm.getState().getValue() == null
                || vm.getState().getValue().getStatus() == MobileLoadState.Status.IDLE) vm.load();
    }

    private void configurePlaylistOfflineAction(MaterialToolbar toolbar, MobileBrowsePayload payload) {
        toolbar.getMenu().clear();
        android.view.MenuItem download = toolbar.getMenu().add(R.string.mobile_offline_playlist_download_action);
        download.setIcon(R.drawable.mobile_ic_download_24);
        download.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        boolean complete = payload != null && !payload.hasMore();
        download.setEnabled(complete);
        download.setOnMenuItemClickListener(item -> {
            if (!complete) {
                Toast.makeText(requireContext(), R.string.mobile_offline_playlist_preparing,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            queuePlaylistOffline(payload);
            return true;
        });
    }

    private void queuePlaylistOffline(MobileBrowsePayload payload) {
        OfflinePlaylistRepository repository = OfflinePlaylistRepository.get(requireContext());
        if (!repository.isEnabled()) {
            Toast.makeText(requireContext(), R.string.mobile_offline_playlist_feature_disabled,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        List<MobileMediaItem> media = new ArrayList<>();
        String thumbnail = "";
        if (payload != null && payload.getSections() != null) {
            for (MobileSection section : payload.getSections()) {
                if (section == null || section.getItems() == null) continue;
                for (MobileMediaItem item : section.getItems()) {
                    if (item == null) continue;
                    media.add(item);
                    if (thumbnail.isEmpty() && item.getThumbnailUrl() != null) {
                        thumbnail = item.getThumbnailUrl();
                    }
                }
            }
        }
        String rawId = getItemId().substring("playlist:".length());
        OfflinePlaylistRecord queued = repository.enqueue(rawId,
                payload == null ? "" : payload.getTitle(), thumbnail, media);
        if (queued == null) {
            Toast.makeText(requireContext(), R.string.mobile_offline_playlist_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        OfflinePlaylistDownloadService.wake(requireContext());
        Toast.makeText(requireContext(), R.string.mobile_offline_playlist_queued,
                Toast.LENGTH_SHORT).show();
    }

    private void setupCategories(ChipGroup group, String selectedPage) {
        int[] labels = {
                R.string.mobile_native_all,
                R.string.mobile_native_new_recommendations,
                R.string.mobile_native_music,
                R.string.mobile_native_live,
                R.string.mobile_native_gaming,
                R.string.mobile_native_news,
                R.string.mobile_native_sports,
                R.string.mobile_native_playlists,
                R.string.mobile_native_history
        };
        String[] pages = {
                "home", "trending", "music", "live", "gaming", "news", "sports",
                "playlists", "history"
        };
        for (int index = 0; index < pages.length; index++) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(labels[index]);
            chip.setCheckable(true);
            chip.setChecked(pages[index].equals(selectedPage));
            final String page = pages[index];
            chip.setOnClickListener(v -> {
                if (!page.equals(getPageId())) {
                    MobileFragmentSupport.navigator(this).openBrowse(page);
                }
            });
            group.addView(chip);
        }
    }

    private boolean isCategoryPage(String pageId) {
        return "home".equals(pageId) || "trending".equals(pageId) || "music".equals(pageId)
                || "live".equals(pageId) || "gaming".equals(pageId) || "news".equals(pageId)
                || "sports".equals(pageId) || "playlists".equals(pageId)
                || "history".equals(pageId);
    }

    private int titleForPage(String pageId) {
        if ("shorts".equals(pageId)) return R.string.mobile_native_shorts;
        if ("subscriptions".equals(pageId)) return R.string.mobile_native_subscriptions;
        if ("notifications".equals(pageId)) return R.string.mobile_native_notifications;
        return 0;
    }

    private List<MobileSection> localizeTopLevelSections(
            List<MobileSection> sections, boolean showCategories) {
        int titleRes = titleForPage(getPageId());
        if (isItemDetail() || showCategories || titleRes == 0 || sections == null || sections.isEmpty()) {
            return sections;
        }
        List<MobileSection> localized = new ArrayList<>(sections);
        MobileSection first = localized.get(0);
        localized.set(0, new MobileSection(first.getId(), getString(titleRes), first.getItems()));
        return localized;
    }
}
