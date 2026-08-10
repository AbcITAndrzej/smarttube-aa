package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;

import java.util.ArrayList;
import java.util.List;

/** Lifecycle-safe Material picker shared by audio, captions and quality selection. */
public final class TrackPickerBottomSheet extends BottomSheetDialogFragment {
    public interface Listener {
        void onTrackSelected(MobileTrack.Type type, MobileTrack track);
    }

    private static final String TAG = "mobile_track_picker";
    private static final String ARG_TYPE = "track_type";
    private static final String ARG_TRACKS = "tracks";
    private static final String ARG_PREFERRED = "preferred";

    public static void show(Fragment target, MobileTrack.Type type, List<MobileTrack> source,
                            String preferredLanguage) {
        if (target == null || type == null || source == null || source.isEmpty()) return;
        FragmentManager manager = target.getFragmentManager();
        if (manager == null || manager.isStateSaved()) return;
        Fragment previous = manager.findFragmentByTag(TAG);
        if (previous instanceof TrackPickerBottomSheet) {
            ((TrackPickerBottomSheet) previous).dismissAllowingStateLoss();
        }
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
        args.putParcelableArrayList(ARG_TRACKS, new ArrayList<>(source));
        args.putString(ARG_PREFERRED, preferredLanguage == null ? "" : preferredLanguage);
        TrackPickerBottomSheet sheet = new TrackPickerBottomSheet();
        sheet.setArguments(args);
        sheet.setTargetFragment(target, 0);
        MobileDiagnostics.info("P16-Tracks", "picker show type=" + type
                + " count=" + source.size() + " orientation="
                + target.getResources().getConfiguration().orientation);
        sheet.show(manager, TAG);
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_track_picker_sheet, container, false);
    }

    @Override public void onViewCreated(@NonNull View content, @Nullable Bundle state) {
        Bundle args = getArguments() == null ? Bundle.EMPTY : getArguments();
        MobileTrack.Type type;
        try {
            type = MobileTrack.Type.valueOf(args.getString(ARG_TYPE, MobileTrack.Type.VIDEO.name()));
        } catch (IllegalArgumentException error) {
            type = MobileTrack.Type.VIDEO;
        }
        ArrayList<MobileTrack> parcelled = args.getParcelableArrayList(ARG_TRACKS);
        List<MobileTrack> tracks = parcelled == null ? new ArrayList<>() : parcelled;
        String preferredLanguage = args.getString(ARG_PREFERRED, "");
        TextView title = content.findViewById(R.id.mobile_track_picker_title);
        TextView hint = content.findViewById(R.id.mobile_track_picker_hint);
        RecyclerView list = content.findViewById(R.id.mobile_track_picker_list);

        int titleRes = type == MobileTrack.Type.VIDEO ? R.string.mobile_native_quality
                : type == MobileTrack.Type.AUDIO ? R.string.mobile_native_audio
                : R.string.mobile_native_subtitles;
        title.setText(titleRes);
        boolean languagePicker = type == MobileTrack.Type.AUDIO
                || type == MobileTrack.Type.SUBTITLE;
        hint.setVisibility(languagePicker ? View.VISIBLE : View.GONE);
        int preferredIndex = languagePicker
                ? PreferredTrackResolver.findPreferred(tracks, preferredLanguage) : -1;
        int selectedIndex = selectedIndex(tracks);
        int initialScrollPosition = preferredIndex >= 0 ? preferredIndex : selectedIndex;
        LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        list.setLayoutManager(layout);
        list.setNestedScrollingEnabled(true);
        final MobileTrack.Type pickerType = type;
        list.setAdapter(new Adapter(tracks, preferredIndex, selectedIndex, track -> {
            Fragment target = getTargetFragment();
            if (target instanceof Listener) {
                ((Listener) target).onTrackSelected(pickerType, track);
            }
            dismissAllowingStateLoss();
        }));
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            ViewGroup.LayoutParams params = list.getLayoutParams();
            params.height = Math.max(dp(180),
                    Math.min(dp(520), getResources().getDisplayMetrics().heightPixels * 2 / 3));
            list.setLayoutParams(params);
        }
        if (initialScrollPosition >= 0) {
            list.post(() -> layout.scrollToPositionWithOffset(initialScrollPosition, dp(12)));
        }
    }

    @Override public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        MobileDiagnostics.info("P16-Tracks", "picker visible orientation="
                + getResources().getConfiguration().orientation);
    }

    @Override public void onCancel(@NonNull DialogInterface dialog) {
        MobileDiagnostics.info("P16-Tracks", "picker cancelled");
        super.onCancel(dialog);
    }

    @Override public void onDismiss(@NonNull DialogInterface dialog) {
        MobileDiagnostics.info("P16-Tracks", "picker dismissed");
        super.onDismiss(dialog);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int selectedIndex(List<MobileTrack> tracks) {
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index) != null && tracks.get(index).isSelected()) return index;
        }
        return -1;
    }

    private interface RowListener { void onClick(MobileTrack track); }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final List<MobileTrack> tracks;
        private final int preferredIndex;
        private final int selectedIndex;
        private final RowListener listener;

        Adapter(List<MobileTrack> tracks, int preferredIndex, int selectedIndex,
                RowListener listener) {
            this.tracks = tracks;
            this.preferredIndex = preferredIndex;
            this.selectedIndex = selectedIndex;
            this.listener = listener;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.mobile_track_picker_row, parent, false);
            return new Holder(view);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            MobileTrack track = tracks.get(position);
            boolean selected = position == selectedIndex || track.isSelected();
            boolean preferred = position == preferredIndex;
            holder.title.setText(track.getLabel());
            holder.language.setText(track.getLanguage().isEmpty()
                    ? holder.itemView.getContext().getString(R.string.mobile_track_language_unknown)
                    : track.getLanguage().toUpperCase(java.util.Locale.ROOT));
            holder.badge.setVisibility(selected || preferred ? View.VISIBLE : View.GONE);
            if (selected && preferred) {
                holder.badge.setText(R.string.mobile_track_badge_selected_default);
            } else if (selected) {
                holder.badge.setText(R.string.mobile_track_badge_selected);
            } else if (preferred) {
                holder.badge.setText(R.string.mobile_track_badge_default);
            }
            holder.card.setChecked(selected);
            holder.itemView.setOnClickListener(v -> listener.onClick(track));
        }

        @Override public int getItemCount() { return tracks.size(); }

        static final class Holder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView title;
            final TextView language;
            final TextView badge;

            Holder(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.mobile_track_row_card);
                title = itemView.findViewById(R.id.mobile_track_row_title);
                language = itemView.findViewById(R.id.mobile_track_row_language);
                badge = itemView.findViewById(R.id.mobile_track_row_badge);
            }
        }
    }
}
