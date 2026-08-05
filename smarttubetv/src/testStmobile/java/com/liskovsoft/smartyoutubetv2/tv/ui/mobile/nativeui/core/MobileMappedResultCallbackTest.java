package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileResultCallback;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobileMappedResultCallbackTest {
    @Test public void mapperFailureBecomesTypedParsingError() {
        RecordingTarget target = new RecordingTarget();
        MobileMappedResultCallback<String, Integer> callback =
                new MobileMappedResultCallback<>(value -> { throw new IllegalStateException("bad"); }, target);
        callback.onSuccess("source");
        assertNull(target.value);
        assertEquals(MobileError.Kind.PARSING, target.error.getKind());
    }

    private static final class RecordingTarget implements MobileResultCallback<Integer> {
        Integer value; MobileError error;
        @Override public void onSuccess(Integer value) { this.value = value; }
        @Override public void onError(MobileError error) { this.error = error; }
    }
}
