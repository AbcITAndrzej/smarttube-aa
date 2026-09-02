#!/usr/bin/env bash
set -euxo pipefail

UPSTREAM_SMARTTUBE_COMMIT="26076c93237172af8e09656d2cfe06ab0d9eb872" # 32.38s
UPSTREAM_MEDIA_SERVICE_CORE="082e2e488cce739f224d0854773fc8d1cf14a48e"
UPSTREAM_SHARED_MODULES="11116d27ed61b25b959f5e6cda17b0090688a131"

for path in \
  common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java \
  common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/VideoLoaderController.java
 do
  curl -fsSL "https://raw.githubusercontent.com/yuliskov/SmartTube/${UPSTREAM_SMARTTUBE_COMMIT}/${path}" -o "${path}"
done

# 32.38 ErrorFixer uses Utils helpers added after the 32.04 application base.
# Keep the 32.38 recovery policy and bridge only those old-common API gaps.
python3 - <<'PY'
from pathlib import Path

p = Path('common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java')
s = p.read_text()
s = s.replace('''        if (Utils.fixRetrofitErrors(getContext(), error)) {\n            return;\n        }\n\n''', '')
s = s.replace('Utils.getFasterDataSource()', 'getFasterDataSource()')
needle = '''    /**\n     * Bad idea. Faster source is different among devices\n     */\n    private boolean isFasterDataSourceEnabled() {'''
helper = '''    private static int getFasterDataSource() {\n        return Utils.skipCronet() ? PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET;\n    }\n\n    /**\n     * Bad idea. Faster source is different among devices\n     */\n    private boolean isFasterDataSourceEnabled() {'''
if needle not in s:
    raise SystemExit('ErrorFixer compatibility anchor not found')
s = s.replace(needle, helper, 1)
p.write_text(s)

# The AA/mobile layer has calls to experimental fallback methods that don't exist
# in official 32.38 VideoLoaderController. Keep ABI compatibility while making
# them inert, so the official 32.38 recovery path is the one actually tested.
p = Path('common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/VideoLoaderController.java')
s = p.read_text()
bridge = '''\n    // AA/mobile compatibility bridge. Official 32.38 recovery remains authoritative.\n    public void reloadVideoAfterStreamRefresh() {\n        reloadVideo();\n    }\n\n    public boolean isProgressiveFallbackActiveForCurrentVideo() {\n        return false;\n    }\n\n    public boolean tryPreserveMultiAudioRecovery(String reason) {\n        return false;\n    }\n\n    public boolean activateProgressiveFallbackForCurrentVideo(String reason) {\n        return false;\n    }\n'''
pos = s.rfind('\n}')
if pos < 0:
    raise SystemExit('VideoLoader class end not found')
s = s[:pos] + bridge + s[pos:]
p.write_text(s)

# New official SharedModules intentionally drops custom AA cache counters. Those
# are diagnostics only, not playback behavior; remove the old dashboard calls.
p = Path('smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/nativeui/diagnostics/MobileDiagnosticsStore.java')
s = p.read_text()
old = '''        SponsorBlockService sponsorService = SponsorBlockService.instance();\n        out.append("SponsorBlockCache: entries=").append(sponsorService.getCacheEntryCount())\n                .append(" inFlight=").append(sponsorService.getInFlightCount())\n                .append(" hits=").append(sponsorService.getCacheHits())\n                .append(" misses=").append(sponsorService.getCacheMisses())\n                .append(" joins=").append(sponsorService.getSingleFlightJoins()).append('\\n');\n        out.append("DeArrowCache: entries=").append(DeArrowService.getCacheEntryCount())\n                .append(" inFlight=").append(DeArrowService.getInFlightCount())\n                .append(" hits=").append(DeArrowService.getCacheHits())\n                .append(" misses=").append(DeArrowService.getCacheMisses())\n                .append(" joins=").append(DeArrowService.getSingleFlightJoins()).append('\\n');\n'''
if old not in s:
    raise SystemExit('MobileDiagnostics cache block not found')
s = s.replace(old, '')
p.write_text(s)
PY

rm -rf SharedModules MediaServiceCore

git clone https://github.com/yuliskov/SharedModules.git SharedModules
git -C SharedModules checkout "${UPSTREAM_SHARED_MODULES}"
rm -rf SharedModules/.git

git clone https://github.com/yuliskov/MediaServiceCore.git MediaServiceCore
git -C MediaServiceCore checkout "${UPSTREAM_MEDIA_SERVICE_CORE}"
git -C MediaServiceCore submodule update --init --recursive
rm -rf MediaServiceCore/.git MediaServiceCore/SharedModules/.git

echo "SmartTube=${UPSTREAM_SMARTTUBE_COMMIT}"
echo "MediaServiceCore=${UPSTREAM_MEDIA_SERVICE_CORE}"
echo "SharedModules=${UPSTREAM_SHARED_MODULES}"
grep -n "switchNextClientNow\|PLAYER_DATA_SOURCE_OKHTTP\|getFasterDataSource" \
  common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java
