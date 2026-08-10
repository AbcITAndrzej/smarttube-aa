package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe continuation coordinator shared by Browse, Search and Channel native-mobile data.
 *
 * <p>SmartTube's media service exposes nested MediaGroup shelves rather than one flat list.
 * AndroidX PagingData would flatten those shelves and lose the continuation owner. This small
 * coordinator keeps the native service model intact while centralising round-robin token choice,
 * completion and accumulated pages.</p>
 */
final class LegacyGroupPaginator {
    private final List<Slot> slots = new ArrayList<>();
    private int cursor;

    LegacyGroupPaginator(List<MediaGroup> initial) {
        if (initial != null) {
            for (MediaGroup group : initial) {
                if (group != null) slots.add(new Slot(group));
            }
        }
    }

    synchronized int nextSlotIndex() {
        if (slots.isEmpty()) return -1;
        for (int offset = 0; offset < slots.size(); offset++) {
            int index = (cursor + offset) % slots.size();
            if (hasNextPage(slots.get(index).tail)) {
                cursor = (index + 1) % slots.size();
                return index;
            }
        }
        return -1;
    }

    synchronized MediaGroup sourceAt(int index) {
        return index < 0 || index >= slots.size() ? null : slots.get(index).tail;
    }

    synchronized void append(int index, MediaGroup next) {
        if (index < 0 || index >= slots.size()) return;
        Slot slot = slots.get(index);
        if (next == null) {
            slot.tail = null;
            return;
        }
        slot.pages.add(next);
        slot.tail = next;
    }

    synchronized void markFinished(int index) {
        if (index >= 0 && index < slots.size()) slots.get(index).tail = null;
    }

    synchronized boolean hasMore() {
        for (Slot slot : slots) if (hasNextPage(slot.tail)) return true;
        return false;
    }

    synchronized List<MediaGroup> firstPages() {
        List<MediaGroup> result = new ArrayList<>();
        for (Slot slot : slots) if (!slot.pages.isEmpty()) result.add(slot.pages.get(0));
        return result;
    }

    synchronized List<List<MediaGroup>> accumulatedPages() {
        List<List<MediaGroup>> result = new ArrayList<>();
        for (Slot slot : slots) result.add(new ArrayList<>(slot.pages));
        return result;
    }

    synchronized int slotCount() { return slots.size(); }

    static boolean hasNextPage(MediaGroup group) {
        if (group == null) return false;
        String key = group.getNextPageKey();
        return key != null && !key.trim().isEmpty();
    }

    private static final class Slot {
        final List<MediaGroup> pages = new ArrayList<>();
        MediaGroup tail;

        Slot(MediaGroup first) {
            pages.add(first);
            tail = first;
        }
    }
}
