package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.*;
import java.text.Normalizer;
import java.util.concurrent.ConcurrentHashMap;

/** Transitional settings adapter: native rows, existing SmartTube category actions. */
public final class LegacySettingsRepository implements MobileSettingsRepository {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, SettingsItem> actions = new ConcurrentHashMap<>();
    private final LegacyErrorMapper errors;

    public LegacySettingsRepository(Context context, LegacyErrorMapper errors) {
        this.context = context;
        this.errors = errors;
    }

    @Override public MobileRequest loadSettings(MobileResultCallback<List<MobileSettingItem>> callback) {
        if (callback == null) return MobileRequest.NONE;
        try {
            List<SettingsItem> legacy = AppDataSourceManager.instance().getSettingItems(context);
            List<MobileSettingItem> result = new ArrayList<>();
            actions.clear();
            for (int i = 0; i < legacy.size(); i++) {
                SettingsItem item = legacy.get(i);
                if (item == null) continue;
                String id = idFor(i, item.title);
                actions.put(id, item);
                result.add(new MobileSettingItem(id, MobileSettingItem.Type.ACTION,
                        item.title, "", "invoke", item.onClick != null, Collections.emptyList()));
            }
            MobileDiagnostics.debug("DataSettings", "loaded categories=" + result.size());
            callback.onSuccess(result);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
        }
        return MobileRequest.NONE;
    }

    @Override public MobileRequest updateSetting(String settingId, String value,
                                                   MobileResultCallback<MobileSettingItem> callback) {
        if (callback == null) return MobileRequest.NONE;
        SettingsItem item = actions.get(settingId);
        if (item == null || item.onClick == null) {
            callback.onError(new MobileError(MobileError.Kind.UNAVAILABLE,
                    "Setting action is no longer available", null, false));
            return MobileRequest.NONE;
        }
        if (!"invoke".equals(value)) {
            callback.onError(new MobileError(MobileError.Kind.PARSING,
                    "Legacy setting categories only accept the invoke action", null, false));
            return MobileRequest.NONE;
        }
        main.post(() -> {
            try {
                MobileDiagnostics.debug("DataSettings", "invoke id=" + settingId);
                item.onClick.run();
                callback.onSuccess(new MobileSettingItem(settingId, MobileSettingItem.Type.ACTION,
                        item.title, "", "invoke", true, Collections.emptyList()));
            } catch (Throwable error) {
                callback.onError(errors.map(error));
            }
        });
        return MobileRequest.NONE;
    }

    static String idFor(int index, String title) {
        String normalized = title == null ? "setting" : Normalizer.normalize(
                title.toLowerCase(Locale.US), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (normalized.isEmpty()) normalized = "setting";
        return "legacy-setting:" + index + ":" + normalized;
    }
}
