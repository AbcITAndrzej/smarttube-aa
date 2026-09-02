from pathlib import Path

p = Path('exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/manifest/SabrManifest.java')
text = p.read_text(encoding='utf-8')
old = '''    static String formatIdentity(String id, long lastModified, String language) {
        return (id != null ? id : "-") + "|" + lastModified + "|" + (language != null ? language : "-");
    }
'''
new = '''    static String formatIdentity(String id, long lastModified, String language) {
        String normalizedLanguage = language != null ? language.toLowerCase(java.util.Locale.US) : "-";
        return (id != null ? id : "-") + "|" + lastModified + "|" + normalizedLanguage;
    }
'''
if text.count(old) != 1:
    raise SystemExit('SabrManifest formatIdentity block not found exactly once')
p.write_text(text.replace(old, new), encoding='utf-8')
print('aa1.31 language identity normalization applied')
