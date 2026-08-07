package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.os.*;
import android.content.res.Configuration;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobileSearchViewModel;

public final class MobileSearchFragment extends Fragment {
    private final Handler suggestionHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSuggestion;

    public static MobileSearchFragment newInstance(String query) {
        MobileSearchFragment f = new MobileSearchFragment();
        Bundle b = new Bundle();
        b.putString("query", query);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,
                                                 @Nullable ViewGroup c,
                                                 @Nullable Bundle s) {
        return i.inflate(R.layout.mobile_native_fragment_search, c, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MobileSearchViewModel vm = new ViewModelProvider(this,
                new MobileNativeViewModelFactory(MobileNativeDependencies.get(), getArguments()))
                .get(MobileSearchViewModel.class);
        EditText query = view.findViewById(R.id.mobile_search_input);
        RecyclerView suggestions = view.findViewById(R.id.mobile_suggestions);
        RecyclerView results = view.findViewById(R.id.mobile_list);
        TextView error = view.findViewById(R.id.mobile_error);
        ProgressBar progress = view.findViewById(R.id.mobile_progress);
        View retry = view.findViewById(R.id.mobile_retry_button);
        MobileTextAdapter suggestionAdapter = new MobileTextAdapter(value -> {
            query.setText(value);
            query.setSelection(value.length());
            submitSearch(vm, query, suggestions);
        });
        final MobileMediaAdapter[] resultAdapterRef = new MobileMediaAdapter[1];
        MobileMediaAdapter resultAdapter = new MobileMediaAdapter(MobileNativeDependencies.get().imageLoader(), item -> {
            if (item.getKind() == MobileMediaItem.Kind.CHANNEL) {
                MobileFragmentSupport.navigator(this).openChannel(item.getId());
            } else if (item.getKind() == MobileMediaItem.Kind.PLAYLIST
                    || item.getKind() == MobileMediaItem.Kind.SECTION_LINK) {
                MobileFragmentSupport.navigator(this).openBrowseItem(item.getId());
            } else if (item.isPlayable()) {
                if (item.getKind() == MobileMediaItem.Kind.SHORT) {
                    MobileFragmentSupport.navigator(this).openShortPlayback(
                            item.getId(), item.getProgressMs(),
                            resultAdapterRef[0].getPlayableIds(MobileMediaItem.Kind.SHORT));
                } else {
                    MobileFragmentSupport.navigator(this).openPlayback(
                            item.getId(), item.getProgressMs());
                }
            }
        });
        resultAdapterRef[0] = resultAdapter;
        suggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        suggestions.setAdapter(suggestionAdapter);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager grid = new GridLayoutManager(requireContext(), 2);
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override public int getSpanSize(int position) {
                    return resultAdapter.getLandscapeSpanSize(position);
                }
            });
            results.setLayoutManager(grid);
        } else {
            results.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        results.setHasFixedSize(true);
        results.setAdapter(resultAdapter);
        query.setText(vm.getQuery());
        query.setSelection(query.length());
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (pendingSuggestion != null) suggestionHandler.removeCallbacks(pendingSuggestion);
                final String requested = s.toString();
                pendingSuggestion = () -> vm.requestSuggestions(requested);
                suggestionHandler.postDelayed(pendingSuggestion, 250);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(vm, query, suggestions);
                return true;
            }
            return false;
        });
        view.findViewById(R.id.mobile_search_submit)
                .setOnClickListener(v -> submitSearch(vm, query, suggestions));
        retry.setOnClickListener(v -> submitSearch(vm, query, suggestions));
        vm.getSuggestions().observe(getViewLifecycleOwner(), values -> {
            suggestionAdapter.submit(values);
            suggestions.setVisibility(values == null || values.isEmpty() ? View.GONE : View.VISIBLE);
        });
        vm.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == MobileLoadState.Status.LOADING && !value.hasData()
                    ? View.VISIBLE : View.GONE);
            error.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            retry.setVisibility(value.getStatus() == MobileLoadState.Status.ERROR ? View.VISIBLE : View.GONE);
            if (value.getError() != null) error.setText(value.getError().getMessage());
            if (value.getData() != null) resultAdapter.submitSections(value.getData().getSections());
        });
        if (!vm.getQuery().isEmpty() && (vm.getState().getValue() == null
                || vm.getState().getValue().getStatus() == MobileLoadState.Status.IDLE)) {
            vm.search(vm.getQuery());
        }
    }

    private void submitSearch(MobileSearchViewModel vm, EditText query, RecyclerView suggestions) {
        if (pendingSuggestion != null) suggestionHandler.removeCallbacks(pendingSuggestion);
        pendingSuggestion = null;
        suggestions.setVisibility(View.GONE);
        query.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
        vm.search(query.getText().toString());
    }

    @Override public void onDestroyView() {
        suggestionHandler.removeCallbacksAndMessages(null);
        pendingSuggestion = null;
        super.onDestroyView();
    }
}
