from pathlib import Path

p = Path('exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/manifest/SabrManifest.java')
text = p.read_text(encoding='utf-8')
old = '''            Log.d(TAG, "V16_FORMAT_ID_BIND selector=%s itag=%s lmt=%s language=%s xtagsHash=%s",
                    selector.displayName, enriched.getItag(),
                    enriched.hasLastModified() ? enriched.getLastModified() : -1,
                    format != null ? valueOrDash(format.language) : "-",
                    Integer.toHexString(xtags.hashCode()));
'''
new = '''            Log.d(TAG, "V16_FORMAT_ID_BIND selector=" + selector.displayName
                    + " itag=" + enriched.getItag()
                    + " lmt=" + (enriched.hasLastModified() ? enriched.getLastModified() : -1)
                    + " language=" + (format != null ? valueOrDash(format.language) : "-")
                    + " xtagsHash=" + Integer.toHexString(xtags.hashCode()));
'''
if text.count(old) != 1:
    raise SystemExit('V16_FORMAT_ID_BIND logging block not found exactly once')
p.write_text(text.replace(old, new), encoding='utf-8')
print('aa1.31 Android Log diagnostic compile fix applied')
