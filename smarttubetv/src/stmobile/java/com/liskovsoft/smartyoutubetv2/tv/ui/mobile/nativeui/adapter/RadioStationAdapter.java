package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Touch-first station rows for the experimental Radio settings screen. */
public final class RadioStationAdapter extends RecyclerView.Adapter<RadioStationAdapter.Holder> {
    public interface Listener {
        void onPlay(RadioStation station);
        void onFavorite(RadioStation station);
    }

    private final Listener listener;
    private final List<RadioStation> stations = new ArrayList<>();

    public RadioStationAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<RadioStation> values) {
        stations.clear();
        if (values != null) stations.addAll(values);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        return stations.get(position).getId().hashCode();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_radio_station_item, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(stations.get(position));
    }

    @Override public int getItemCount() { return stations.size(); }

    final class Holder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final TextView meta;
        private final TextView tags;
        private final ImageButton favorite;
        private final MaterialButton play;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.mobile_radio_station_icon);
            name = itemView.findViewById(R.id.mobile_radio_station_name);
            meta = itemView.findViewById(R.id.mobile_radio_station_meta);
            tags = itemView.findViewById(R.id.mobile_radio_station_tags);
            favorite = itemView.findViewById(R.id.mobile_radio_station_favorite);
            play = itemView.findViewById(R.id.mobile_radio_station_play);
        }

        void bind(RadioStation station) {
            name.setText(station.getName());
            String codec = station.getCodec().isEmpty()
                    ? "STREAM" : station.getCodec().toUpperCase(Locale.US);
            String bitrate = station.getBitrate() > 0
                    ? Integer.toString(station.getBitrate()) : "?";
            String stationMeta = itemView.getContext().getString(R.string.mobile_radio_station_meta,
                    codec, bitrate, station.getClickCount());
            String country = station.getCountry().isEmpty()
                    ? station.getCountryCode() : station.getCountry();
            meta.setText(country.isEmpty() ? stationMeta : country + " • " + stationMeta);
            String compactTags = compactTags(station.getTags());
            tags.setText(compactTags);
            tags.setVisibility(compactTags.isEmpty() ? View.GONE : View.VISIBLE);
            favorite.setImageResource(station.isFavorite()
                    ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            favorite.setContentDescription(itemView.getContext().getString(station.isFavorite()
                    ? R.string.mobile_radio_remove_favorite : R.string.mobile_radio_add_favorite));
            if (station.getFaviconUrl().isEmpty()) {
                Glide.with(icon).clear(icon);
                icon.setImageResource(R.mipmap.app_icon);
            } else {
                Glide.with(icon).load(station.getFaviconUrl()).centerCrop()
                        .placeholder(R.mipmap.app_icon).error(R.mipmap.app_icon).into(icon);
            }
            itemView.setContentDescription(station.getName() + ", " + meta.getText());
            itemView.setOnClickListener(view -> listener.onPlay(station));
            play.setOnClickListener(view -> listener.onPlay(station));
            favorite.setOnClickListener(view -> listener.onFavorite(station));
        }
    }

    private static String compactTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) return "";
        String[] values = tags.split(",");
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            String clean = value.trim();
            if (clean.isEmpty()) continue;
            if (result.length() > 0) result.append(" • ");
            result.append(clean);
            if (result.length() >= 52) break;
        }
        return result.toString();
    }
}
