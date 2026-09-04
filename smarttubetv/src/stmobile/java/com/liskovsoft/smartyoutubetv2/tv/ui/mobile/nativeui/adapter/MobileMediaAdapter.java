package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileImageLoader;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MobileMediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener { void onMediaClicked(MobileMediaItem item); }
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_MEDIA = 2;
    private static final int TYPE_SHORT_PAIR = 3;

    private final List<Row> rows = new ArrayList<>();
    private final MobileImageLoader imageLoader;
    private final Listener listener;

    public MobileMediaAdapter(MobileImageLoader imageLoader, Listener listener) {
        this.imageLoader = imageLoader;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitSections(List<MobileSection> sections) {
        List<Row> next = buildRows(sections);
        if (rows.isEmpty()) {
            rows.addAll(next);
            if (!next.isEmpty()) notifyItemRangeInserted(0, next.size());
            return;
        }

        // Continuation pages normally append to an unchanged prefix. Handle that hot path without
        // re-binding every visible card and without paying DiffUtil cost for the whole feed.
        if (isPureAppend(rows, next)) {
            int start = rows.size();
            if (next.size() > start) {
                rows.addAll(next.subList(start, next.size()));
                notifyItemRangeInserted(start, next.size() - start);
            }
            return;
        }

        List<Row> previous = new ArrayList<>(rows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return previous.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return previous.get(oldItemPosition).key.equals(next.get(newItemPosition).key);
            }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return previous.get(oldItemPosition).sameContent(next.get(newItemPosition));
            }
        }, false);
        rows.clear();
        rows.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    private static List<Row> buildRows(List<MobileSection> sections) {
        List<Row> result = new ArrayList<>();
        for (MobileSection section : sections == null ? Collections.<MobileSection>emptyList() : sections) {
            if (!section.getTitle().isEmpty()) result.add(Row.header(section.getId(), section.getTitle()));
            List<MobileMediaItem> items = section.getItems();
            for (int index = 0; index < items.size();) {
                MobileMediaItem item = items.get(index);
                if (item.getKind() == MobileMediaItem.Kind.SHORT) {
                    MobileMediaItem second = index + 1 < items.size()
                            && items.get(index + 1).getKind() == MobileMediaItem.Kind.SHORT
                            ? items.get(index + 1) : null;
                    result.add(Row.shortPair(section.getId(), index, item, second));
                    index += second == null ? 1 : 2;
                } else {
                    result.add(Row.media(section.getId(), index, item));
                    index++;
                }
            }
        }
        return result;
    }

    private static boolean isPureAppend(List<Row> oldRows, List<Row> newRows) {
        if (newRows.size() < oldRows.size()) return false;
        for (int index = 0; index < oldRows.size(); index++) {
            Row oldRow = oldRows.get(index);
            Row newRow = newRows.get(index);
            if (!oldRow.key.equals(newRow.key) || !oldRow.sameContent(newRow)) return false;
        }
        return true;
    }

    /** Current visible queue, kept in feed order and de-duplicated by media id. */
    public ArrayList<String> getPlayableIds(MobileMediaItem.Kind kind) {
        ArrayList<String> result = new ArrayList<>();
        for (Row row : rows) {
            appendPlayableId(result, row.item, kind);
            appendPlayableId(result, row.secondItem, kind);
        }
        return result;
    }

    /** Regular-video queue in the same order as the currently rendered feed. */
    public ArrayList<String> getRegularPlayableIds() {
        ArrayList<String> result = new ArrayList<>();
        for (Row row : rows) {
            appendRegularPlayableId(result, row.item);
            appendRegularPlayableId(result, row.secondItem);
        }
        return result;
    }

    private static void appendRegularPlayableId(List<String> result, MobileMediaItem item) {
        if (item == null || !item.isPlayable() || item.getKind() == MobileMediaItem.Kind.SHORT
                || item.getId() == null || result.contains(item.getId())) return;
        result.add(item.getId());
    }

    private static void appendPlayableId(List<String> result, MobileMediaItem item,
                                         MobileMediaItem.Kind kind) {
        if (item == null || !item.isPlayable() || item.getKind() != kind
                || item.getId() == null || result.contains(item.getId())) return;
        result.add(item.getId());
    }

    @Override public long getItemId(int position) { return rows.get(position).stableId(); }
    @Override public int getItemViewType(int position) {
        Row row = rows.get(position);
        if (row.item == null) return TYPE_HEADER;
        return row.shortPair ? TYPE_SHORT_PAIR : TYPE_MEDIA;
    }
    @Override public int getItemCount() { return rows.size(); }

    /** Section headers and Shorts pairs occupy the full two-column grid width. */
    public int getGridSpanSize(int position) {
        int type = getItemViewType(position);
        return type == TYPE_MEDIA ? 1 : 2;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.mobile_native_item_section_header, parent, false));
        }
        if (viewType == TYPE_SHORT_PAIR) {
            return new ShortPairHolder(inflater.inflate(R.layout.mobile_native_item_short_pair, parent, false));
        }
        return new MediaHolder(inflater.inflate(R.layout.mobile_native_item_media, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) ((HeaderHolder) holder).title.setText(row.header);
        else if (holder instanceof ShortPairHolder) ((ShortPairHolder) holder).bind(row.item, row.secondItem);
        else ((MediaHolder) holder).bind(row.item);
    }

    @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof MediaHolder) imageLoader.clear(((MediaHolder) holder).thumbnail);
        if (holder instanceof ShortPairHolder) ((ShortPairHolder) holder).clearImages();
    }

    private final class ShortPairHolder extends RecyclerView.ViewHolder {
        final View left;
        final View right;
        final ImageView leftImage;
        final ImageView rightImage;
        final TextView leftTitle;
        final TextView rightTitle;

        ShortPairHolder(View view) {
            super(view);
            left = view.findViewById(R.id.mobile_short_left);
            right = view.findViewById(R.id.mobile_short_right);
            leftImage = left.findViewById(R.id.mobile_short_thumbnail);
            rightImage = right.findViewById(R.id.mobile_short_thumbnail);
            leftTitle = left.findViewById(R.id.mobile_short_title);
            rightTitle = right.findViewById(R.id.mobile_short_title);
        }

        void bind(MobileMediaItem first, MobileMediaItem second) {
            bindShort(left, leftImage, leftTitle, first);
            bindShort(right, rightImage, rightTitle, second);
        }

        void clearImages() {
            imageLoader.clear(leftImage);
            imageLoader.clear(rightImage);
        }

        private void bindShort(View card, ImageView image, TextView title, MobileMediaItem item) {
            card.setVisibility(item == null ? View.INVISIBLE : View.VISIBLE);
            if (item == null) return;
            title.setText(item.getTitle());
            imageLoader.load(image, item.getThumbnailUrl());
            card.setOnClickListener(v -> listener.onMediaClicked(item));
        }
    }

    private final class MediaHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView subtitle;
        final TextView duration;
        MediaHolder(View view) {
            super(view);
            thumbnail = view.findViewById(R.id.mobile_thumbnail);
            title = view.findViewById(R.id.mobile_title);
            subtitle = view.findViewById(R.id.mobile_subtitle);
            duration = view.findViewById(R.id.mobile_duration);
        }
        void bind(final MobileMediaItem item) {
            title.setText(item.getTitle());
            subtitle.setText(item.getSubtitle());
            subtitle.setVisibility(item.getSubtitle().isEmpty() ? View.GONE : View.VISIBLE);
            duration.setText(item.getDurationText());
            duration.setVisibility(item.getDurationText().isEmpty() ? View.GONE : View.VISIBLE);
            imageLoader.load(thumbnail, item.getThumbnailUrl());
            boolean enabled = item.isPlayable()
                    || item.getKind() == MobileMediaItem.Kind.CHANNEL
                    || item.getKind() == MobileMediaItem.Kind.PLAYLIST
                    || item.getKind() == MobileMediaItem.Kind.SECTION_LINK;
            itemView.setEnabled(enabled);
            itemView.setAlpha(enabled ? 1f : 0.5f);
            itemView.setOnClickListener(v -> listener.onMediaClicked(item));
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderHolder(View view) { super(view); title = (TextView) view; }
    }

    private static final class Row {
        final String key;
        final String header;
        final MobileMediaItem item;
        final MobileMediaItem secondItem;
        final boolean shortPair;
        private Row(String key, String header, MobileMediaItem item,
                    MobileMediaItem secondItem, boolean shortPair) {
            this.key = key == null ? "" : key;
            this.header = header;
            this.item = item;
            this.secondItem = secondItem;
            this.shortPair = shortPair;
        }
        static Row header(String key, String value) { return new Row("header:" + key, value, null, null, false); }
        static Row media(String sectionId, int position, MobileMediaItem item) {
            return new Row("media:" + sectionId + ":" + position + ":" + item.getId(),
                    null, item, null, false);
        }
        static Row shortPair(String sectionId, int position,
                             MobileMediaItem first, MobileMediaItem second) {
            String secondId = second == null ? "" : ":" + second.getId();
            return new Row("short:" + sectionId + ":" + position + ":" + first.getId() + secondId,
                    null, first, second, true);
        }
        boolean sameContent(Row other) {
            return other != null
                    && Objects.equals(header, other.header)
                    && sameItem(item, other.item)
                    && sameItem(secondItem, other.secondItem)
                    && shortPair == other.shortPair;
        }
        private static boolean sameItem(MobileMediaItem left, MobileMediaItem right) {
            if (left == right) return true;
            if (left == null || right == null) return false;
            return Objects.equals(left.getId(), right.getId())
                    && left.getKind() == right.getKind()
                    && Objects.equals(left.getTitle(), right.getTitle())
                    && Objects.equals(left.getSubtitle(), right.getSubtitle())
                    && Objects.equals(left.getThumbnailUrl(), right.getThumbnailUrl())
                    && Objects.equals(left.getDurationText(), right.getDurationText())
                    && left.getProgressMs() == right.getProgressMs()
                    && left.getDurationMs() == right.getDurationMs()
                    && left.isPlayable() == right.isPlayable();
        }
        long stableId() { return key.hashCode(); }
    }
}
