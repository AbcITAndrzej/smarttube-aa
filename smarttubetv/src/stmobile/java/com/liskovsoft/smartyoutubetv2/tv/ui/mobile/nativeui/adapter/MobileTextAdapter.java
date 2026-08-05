package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileTextAdapter extends RecyclerView.Adapter<MobileTextAdapter.Holder> {
    public interface Listener { void onTextClicked(String value); }
    private final List<String> items = new ArrayList<>();
    private final Listener listener;

    public MobileTextAdapter(Listener listener) { this.listener = listener; }
    public void submit(List<String> value) {
        items.clear();
        items.addAll(value == null ? Collections.<String>emptyList() : value);
        notifyDataSetChanged();
    }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_native_item_text, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        String value = items.get(position);
        holder.text.setText(value);
        holder.itemView.setOnClickListener(v -> listener.onTextClicked(value));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        Holder(View view) { super(view); text = (TextView) view; }
    }
}
