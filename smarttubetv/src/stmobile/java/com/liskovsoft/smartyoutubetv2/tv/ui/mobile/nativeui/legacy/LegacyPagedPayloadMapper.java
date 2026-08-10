package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maps accumulated continuation pages while preserving logical shelves and removing duplicates. */
final class LegacyPagedPayloadMapper {
    private LegacyPagedPayloadMapper() { }

    static List<MobileSection> map(LegacyMediaMapper mapper, LegacyGroupPaginator paginator) {
        List<MobileSection> result = new ArrayList<>();
        List<List<MediaGroup>> slots = paginator.accumulatedPages();
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            List<MediaGroup> pages = slots.get(slotIndex);
            MobileSection first = null;
            List<MobileMediaItem> items = new ArrayList<>();
            Set<String> ids = new LinkedHashSet<>();
            for (MediaGroup page : pages) {
                if (page == null) continue;
                MobileSection mapped = mapper.map(page, slotIndex);
                if (first == null) first = mapped;
                if (mapped == null) continue;
                for (MobileMediaItem item : mapped.getItems()) {
                    if (item == null || item.getId() == null || !ids.add(item.getId())) continue;
                    items.add(item);
                }
            }
            if (first != null && !items.isEmpty()) {
                result.add(new MobileSection(first.getId(), first.getTitle(), items));
            }
        }
        return result;
    }
}
