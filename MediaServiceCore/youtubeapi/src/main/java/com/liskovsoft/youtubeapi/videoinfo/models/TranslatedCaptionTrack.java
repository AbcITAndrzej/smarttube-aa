package com.liskovsoft.youtubeapi.videoinfo.models;

import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class TranslatedCaptionTrack extends CaptionTrack {
    public final static String TRANSLATE_MARKER = "*";
    private final CaptionTrack mOriginTrack;
    private final String mLanguageCode;
    private final String mLanguageName;
    private final String mIdSuffix;

    public TranslatedCaptionTrack(CaptionTrack originTrack, TranslationLanguage language) {
        this(originTrack, language.getLanguageCode(), language.getLanguageName());
    }

    public TranslatedCaptionTrack(CaptionTrack originTrack, String languageCode, String languageName) {
        this(originTrack, languageCode, languageName, languageCode);
    }

    public TranslatedCaptionTrack(CaptionTrack originTrack, String languageCode, String languageName,
                                  String idSuffix) {
        mOriginTrack = originTrack;
        mLanguageCode = languageCode;
        mLanguageName = languageName;
        mIdSuffix = idSuffix;
    }

    @Override
    public String getBaseUrl() {
        // Don't try to translate the same lang or you'll get a mess
        if (Helpers.equals(mOriginTrack.getLanguageCode(), mLanguageCode)) {
            return mOriginTrack.getBaseUrl();
        }

        return mOriginTrack.getBaseUrl() + "&tlang=" + mLanguageCode;
    }

    @Override
    public boolean isTranslatable() {
        return mOriginTrack.isTranslatable();
    }

    @Override
    public String getLanguageCode() {
        return mLanguageCode;
    }

    @Override
    public String getVssId() {
        String originId = mOriginTrack.getVssId();
        return (originId != null ? originId : "caption") + ".tlang." + mIdSuffix;
    }

    @Override
    public String getName() {
        // NOTE: tag contain weird chars: (simplified) - chinese (simplified)
        //return mLanguage.getLanguageName() + (mTag != null ? " " + mTag : "") + TRANSLATE_MARKER;

        return YouTubeHelper.exoNameFix(mLanguageName) + TRANSLATE_MARKER;
    }

    @Override
    public String getType() {
        return mOriginTrack.getType();
    }

    @Override
    public String getMimeType() {
        return mOriginTrack.getMimeType();
    }

    @Override
    public String getCodecs() {
        return mOriginTrack.getCodecs();
    }

    // Doesn't work!!!!
    private String countryCodeToFlag(String countryCode) {
        int firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6;
        int secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6;
        return new String(Character.toChars(firstLetter)) + new String(Character.toChars(secondLetter));
    }
}
