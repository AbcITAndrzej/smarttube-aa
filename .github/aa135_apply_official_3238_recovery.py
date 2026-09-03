#!/usr/bin/env python3
from pathlib import Path
import subprocess

UPSTREAM = "26076c93237172af8e09656d2cfe06ab0d9eb872"  # SmartTube 32.38s


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"aa1.35: anchor not found: {label}")
    return text.replace(old, new, 1)

# 1) Use the official 32.38 error-recovery policy, but DO NOT replace MediaServiceCore
# or SharedModules. This keeps the 32.04/AA account/sign-in implementation intact.
error_path = Path("common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java")
url = f"https://raw.githubusercontent.com/yuliskov/SmartTube/{UPSTREAM}/{error_path.as_posix()}"
subprocess.run(["curl", "-fsSL", url, "-o", str(error_path)], check=True)
err = error_path.read_text()

# 32.38 Utils has helpers that the 32.04 application base doesn't. Keep the same
# policy with equivalent local compatibility code.
err = err.replace('''        if (Utils.fixRetrofitErrors(getContext(), error)) {\n            return;\n        }\n\n''', '')
err = err.replace('Utils.getFasterDataSource()', 'getFasterDataSource()')
needle = '''    /**\n     * Bad idea. Faster source is different among devices\n     */\n    private boolean isFasterDataSourceEnabled() {'''
helper = '''    private static int getFasterDataSource() {\n        return Utils.skipCronet() ? PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET;\n    }\n\n    /**\n     * Bad idea. Faster source is different among devices\n     */\n    private boolean isFasterDataSourceEnabled() {'''
err = replace_once(err, needle, helper, "ErrorFixer getFasterDataSource bridge")
error_path.write_text(err)

# 2) Extend only the playback-control interface. Old sign-in methods and services are untouched.
iface_path = Path("MediaServiceCore/mediaserviceinterfaces/src/main/java/com/liskovsoft/mediaserviceinterfaces/ServiceManager.java")
iface = iface_path.read_text()
iface = replace_once(
    iface,
    '''    void refreshCacheIfNeeded();\n    void applyNoPlaybackFix();\n    void applySubtitleFix();\n''',
    '''    void refreshCacheIfNeeded();\n    void switchNextClient();\n    void switchNextClientNow();\n    void switchNextSubsFormat();\n    // Compatibility aliases used by the older 32.04/AA application layer.\n    void applyNoPlaybackFix();\n    void applySubtitleFix();\n''',
    "ServiceManager playback methods")
iface_path.write_text(iface)

# 3) Port the 32.38 force-client-switch API into the OLD MediaServiceCore manager.
# This deliberately leaves YouTubeSignInService and all auth/account classes untouched.
manager_path = Path("MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeServiceManager.java")
manager = manager_path.read_text()
old_manager = '''    @Override\n    public void applyNoPlaybackFix() {\n        getYouTubeMediaItemService().invalidateCache();\n        getVideoInfoService().switchNextFormat();\n    }\n\n    @Override\n    public void applySubtitleFix() {\n        getYouTubeMediaItemService().invalidateCache();\n        getVideoInfoService().switchNextSubtitle();\n    }\n'''
new_manager = '''    @Override\n    public void switchNextClient() {\n        getYouTubeMediaItemService().invalidateCache();\n        getVideoInfoService().switchNextFormat(false);\n    }\n\n    @Override\n    public void switchNextClientNow() {\n        getYouTubeMediaItemService().invalidateCache();\n        getVideoInfoService().switchNextFormat(true);\n    }\n\n    @Override\n    public void switchNextSubsFormat() {\n        getYouTubeMediaItemService().invalidateCache();\n        getVideoInfoService().switchNextSubtitle();\n    }\n\n    @Override\n    public void applyNoPlaybackFix() {\n        switchNextClient();\n    }\n\n    @Override\n    public void applySubtitleFix() {\n        switchNextSubsFormat();\n    }\n'''
manager = replace_once(manager, old_manager, new_manager, "YouTubeServiceManager force switch")
manager_path.write_text(manager)

# 4) Port the exact 32.38 semantic difference that matters here: force=true skips the
# PoToken-cache retry and immediately rotates the /player client. Keep the existing AA
# format parsing/multi-audio code and account/auth flow.
video_path = Path("MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/V2/VideoInfoService.java")
video = video_path.read_text()
old_sig = '''    public void switchNextFormat() {\n        //initInfoTypeIfNeeded();\n'''
new_sig = '''    public void switchNextFormat(boolean force) {\n        if (force) {\n            nextVideoInfoType();\n            Log.d(TAG, "P18_OFFICIAL_3238 force client switch next=%s", mNextInfoType);\n            return;\n        }\n\n        //initInfoTypeIfNeeded();\n'''
video = replace_once(video, old_sig, new_sig, "VideoInfoService switchNextFormat(force)")
anchor = '''    public void switchNextSubtitle() {\n'''
overload = '''    // Compatibility overload for older AA callers.\n    public void switchNextFormat() {\n        switchNextFormat(false);\n    }\n\n    public void switchNextSubtitle() {\n'''
video = replace_once(video, anchor, overload, "VideoInfoService compatibility overload")
video_path.write_text(video)

# 5) The AA/mobile 8 s watchdog used to bypass the official policy and force the custom
# progressive fallback. Route it through the same immediate 32.38 client rotation instead.
mobile_path = Path("smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/nativeui/legacy/LegacyMobilePlaybackRepository.java")
mobile = mobile_path.read_text()
import_anchor = 'import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;\n'
mobile = replace_once(
    mobile,
    import_anchor,
    import_anchor + 'import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;\n',
    "LegacyMobilePlaybackRepository YouTubeServiceManager import")
old_watchdog = '''                        // Common ErrorFixer may have switched sources at 7 s already.\n                        // Do not undo that recovery with another metadata/SABR reload.\n                        if (loader.isProgressiveFallbackActiveForCurrentVideo()) return true;\n                        if (loader.tryPreserveMultiAudioRecovery("mobile-watchdog")) {\n                            MobileDiagnostics.info("V16-MultiAudio",\n                                    "mobile watchdog preserved adaptive multi-audio");\n                            return true;\n                        }\n                        if (loader.activateProgressiveFallbackForCurrentVideo("mobile-watchdog")) {\n                            return true;\n                        }\n\n                        loader.reloadVideo();\n                        return false;\n'''
new_watchdog = '''                        // Match SmartTube 32.38: a startup stall invalidates the current\n                        // /player client immediately instead of degrading to muxed progressive.\n                        MobileDiagnostics.info("P18-Official3238",\n                                "mobile watchdog -> force client switch");\n                        YouTubeServiceManager.instance().switchNextClientNow();\n                        loader.reloadVideo();\n                        return true;\n'''
mobile = replace_once(mobile, old_watchdog, new_watchdog, "mobile startup watchdog")
mobile_path.write_text(mobile)

print("aa1.35 applied: official 32.38 ErrorFixer + forced client rotation; auth/core preserved")
