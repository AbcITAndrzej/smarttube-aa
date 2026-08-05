package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class MobileSettingsViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    @Test public void updateReplacesOnlyMatchingSetting() {
        MobileSettingItem before = new MobileSettingItem("audio", MobileSettingItem.Type.SWITCH,
                "Audio", "", "false", true, Collections.emptyList());
        MobileSettingItem other = new MobileSettingItem("theme", MobileSettingItem.Type.CHOICE,
                "Theme", "", "dark", true, Arrays.asList("dark", "light"));
        FakeSettings repo = new FakeSettings(Arrays.asList(before, other));
        MobileSettingsViewModel vm = new MobileSettingsViewModel(repo);
        vm.load();
        vm.update(before, "true");
        List<MobileSettingItem> result = vm.getState().getValue().getData();
        assertTrue(result.get(0).isChecked());
        assertEquals("dark", result.get(1).getValue());
    }

    private static final class FakeSettings implements MobileSettingsRepository {
        private final List<MobileSettingItem> values;
        FakeSettings(List<MobileSettingItem> values) { this.values = values; }
        @Override public MobileRequest loadSettings(MobileResultCallback<List<MobileSettingItem>> callback) {
            callback.onSuccess(values); return MobileRequest.NONE;
        }
        @Override public MobileRequest updateSetting(String id, String value,
                                                     MobileResultCallback<MobileSettingItem> callback) {
            for (MobileSettingItem item : values) {
                if (id.equals(item.getId())) {
                    callback.onSuccess(new MobileSettingItem(item.getId(), item.getType(), item.getTitle(),
                            item.getSummary(), value, item.isEnabled(), item.getOptions()));
                    return MobileRequest.NONE;
                }
            }
            callback.onError(new MobileError(MobileError.Kind.UNKNOWN, "missing", null, false));
            return MobileRequest.NONE;
        }
    }
}
