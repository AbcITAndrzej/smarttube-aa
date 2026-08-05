package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSettingItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileSettingsAdapter extends RecyclerView.Adapter<MobileSettingsAdapter.Holder> {
    public interface Listener { void onSettingClicked(MobileSettingItem item); }
    private final List<MobileSettingItem> items = new ArrayList<>();
    private final Listener listener;

    public MobileSettingsAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<MobileSettingItem> value) {
        items.clear();
        items.addAll(value == null ? Collections.<MobileSettingItem>emptyList() : value);
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return items.size(); }

    @Override public long getItemId(int position) {
        String id = items.get(position).getId();
        return id == null ? position : id.hashCode();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_native_item_setting, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;
        final TextView value;
        final SwitchMaterial toggle;

        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.mobile_setting_title);
            summary = view.findViewById(R.id.mobile_setting_summary);
            value = view.findViewById(R.id.mobile_setting_value);
            toggle = view.findViewById(R.id.mobile_setting_switch);
            toggle.setClickable(false);
            toggle.setFocusable(false);
        }

        void bind(final MobileSettingItem item) {
            title.setText(item.getTitle());
            summary.setText(item.getSummary());
            summary.setVisibility(item.getSummary().isEmpty() ? View.GONE : View.VISIBLE);
            boolean isSwitch = item.getType() == MobileSettingItem.Type.SWITCH;
            toggle.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
            toggle.setChecked(item.isChecked());
            boolean showValue = item.getType() == MobileSettingItem.Type.CHOICE
                    || item.getType() == MobileSettingItem.Type.SLIDER;
            value.setVisibility(showValue ? View.VISIBLE : View.GONE);
            value.setText(item.getValue());
            itemView.setEnabled(item.isEnabled());
            itemView.setAlpha(item.isEnabled() ? 1f : 0.5f);
            itemView.setOnClickListener(v -> listener.onSettingClicked(item));
        }
    }
}
