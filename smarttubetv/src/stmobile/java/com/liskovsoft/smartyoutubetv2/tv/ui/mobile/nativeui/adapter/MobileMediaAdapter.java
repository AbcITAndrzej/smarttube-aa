package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileImageLoader;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileMediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener { void onMediaClicked(MobileMediaItem item); }
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_MEDIA = 2;

    private final List<Row> rows = new ArrayList<>();
    private final MobileImageLoader imageLoader;
    private final Listener listener;

    public MobileMediaAdapter(MobileImageLoader imageLoader, Listener listener) {
        this.imageLoader = imageLoader;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitSections(List<MobileSection> sections) {
        rows.clear();
        for (MobileSection section : sections == null ? Collections.<MobileSection>emptyList() : sections) {
            if (!section.getTitle().isEmpty()) rows.add(Row.header(section.getId(), section.getTitle()));
            for (MobileMediaItem item : section.getItems()) rows.add(Row.media(item));
        }
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) { return rows.get(position).stableId(); }
    @Override public int getItemViewType(int position) { return rows.get(position).item == null ? TYPE_HEADER : TYPE_MEDIA; }
    @Override public int getItemCount() { return rows.size(); }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.mobile_native_item_section_header, parent, false));
        }
        return new MediaHolder(inflater.inflate(R.layout.mobile_native_item_media, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) ((HeaderHolder) holder).title.setText(row.header);
        else ((MediaHolder) holder).bind(row.item);
    }

    @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof MediaHolder) imageLoader.clear(((MediaHolder) holder).thumbnail);
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
            itemView.setEnabled(item.isPlayable() || item.getKind() == MobileMediaItem.Kind.CHANNEL);
            itemView.setAlpha(itemView.isEnabled() ? 1f : 0.5f);
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
        private Row(String key, String header, MobileMediaItem item) {
            this.key = key == null ? "" : key;
            this.header = header;
            this.item = item;
        }
        static Row header(String key, String value) { return new Row("header:" + key, value, null); }
        static Row media(MobileMediaItem item) { return new Row("media:" + item.getId(), null, item); }
        long stableId() { return key.hashCode(); }
    }
}
