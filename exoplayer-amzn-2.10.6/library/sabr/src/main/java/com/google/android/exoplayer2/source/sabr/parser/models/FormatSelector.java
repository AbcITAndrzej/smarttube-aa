package com.google.android.exoplayer2.source.sabr.parser.models;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.util.Log;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FormatSelector {
    private static final String TAG = FormatSelector.class.getSimpleName();
    public final String displayName;
    public final List<FormatId> formatIds = new ArrayList<>();
    public final List<Format> formats = new ArrayList<>();
    public final boolean discardMedia;
    private boolean strictCollisionLogged;

    public FormatSelector(String displayName, boolean discardMedia) {
        this(displayName, discardMedia, (FormatId[]) null);
    }

    public FormatSelector(String displayName, boolean discardMedia, FormatId... formatIds) {
        this.displayName = displayName;
        this.discardMedia = discardMedia;

        if (formatIds != null) {
            this.formatIds.addAll(Arrays.asList(formatIds));
        }
    }

    public FormatSelector(String displayName, boolean discardMedia, Format... formats) {
        this.displayName = displayName;
        this.discardMedia = discardMedia;

        if (formats != null) {
            for (Format format : formats) {
                this.formatIds.add(createFormatId(format));
            }
            this.formats.addAll(Arrays.asList(formats));
        }
    }

    public String getMimePrefix() {
        return null;
    }

    public boolean match(FormatId formatId, String mimeType) {
        if (formatIds.contains(formatId)) {
            return true;
        }

        if (formatIds.isEmpty()) {
            return getMimePrefix() != null && mimeType != null
                    && mimeType.toLowerCase().startsWith(getMimePrefix());
        }

        for (FormatId expected : formatIds) {
            if (!expected.hasItag() || !formatId.hasItag()
                    || expected.getItag() != formatId.getItag()) {
                continue;
            }

            // YouTube may reuse one itag for multiple logical audio variants
            // (original, DRC, voice-boost, auto-dub). When both sides expose
            // last_modified it is part of the SABR format identity and must
            // match. Falling back to itag alone mixes chunks from different
            // representations into one extractor and causes endless init loads.
            if (expected.hasLastModified() && formatId.hasLastModified()) {
                if (expected.getLastModified() == formatId.getLastModified()) {
                    return true;
                }
                if (!strictCollisionLogged) {
                    strictCollisionLogged = true;
                    Log.w(TAG, "AA125 rejected same-itag SABR collision: itag="
                            + formatId.getItag() + " expectedLmt=" + expected.getLastModified()
                            + " actualLmt=" + formatId.getLastModified());
                }
                continue;
            }

            // Legacy/older SABR responses can omit last_modified. Keep the
            // historical itag fallback only for those incomplete identities.
            return true;
        }

        return false;
    }

    public boolean isDiscardMedia() {
        return discardMedia;
    }

    public @Nullable Format getSelectedFormat() {
        return !formats.isEmpty() ? formats.get(0) : null;
    }

    public @Nullable FormatId getSelectedFormatId() {
        return !formatIds.isEmpty() ? formatIds.get(0) : null;
    }

    private static FormatId createFormatId(Format format) {
        FormatId formatId = FormatId.newBuilder()
                .setItag(Helpers.parseInt(format.id))
                .setLastModified(format.lastModified)
                .build();
        return formatId;
    }
}
