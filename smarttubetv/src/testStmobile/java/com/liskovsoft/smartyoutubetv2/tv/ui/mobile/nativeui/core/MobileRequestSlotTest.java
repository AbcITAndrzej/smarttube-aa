package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobileRequestSlotTest {
    @Test public void replacingRequestCancelsPreviousAndInvalidatesCallback() {
        MobileRequestSlot slot = new MobileRequestSlot();
        FlagRequest first = new FlagRequest();
        long firstToken = slot.begin(); slot.attach(firstToken, first);
        long secondToken = slot.begin();
        assertTrue(first.cancelled);
        assertFalse(slot.isCurrent(firstToken));
        assertTrue(slot.isCurrent(secondToken));
    }
    @Test public void lateAttachIsCancelled() {
        MobileRequestSlot slot = new MobileRequestSlot();
        long stale = slot.begin(); slot.begin();
        FlagRequest request = new FlagRequest(); slot.attach(stale, request);
        assertTrue(request.cancelled);
    }
    private static final class FlagRequest implements MobileRequest {
        boolean cancelled;
        @Override public void cancel() { cancelled = true; }
    }
}
