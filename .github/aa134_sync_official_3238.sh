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

# 32.38 ErrorFixer uses two Utils helpers added after the 32.04 application base.
# Keep the 32.38 playback/error policy, but bridge those helpers with the equivalent
# 32.04-compatible logic so the transplant remains minimal and testable.
python3 - <<'PY'
from pathlib import Path
p = Path('common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java')
s = p.read_text()
s = s.replace('''        if (Utils.fixRetrofitErrors(getContext(), error)) {\n            return;\n        }\n\n''', '')
s = s.replace('Utils.getFasterDataSource()', 'getFasterDataSource()')
needle = '''    /**\n     * Bad idea. Faster source is different among devices\n     */\n    private boolean isFasterDataSourceEnabled() {'''
helper = '''    private static int getFasterDataSource() {\n        return Utils.skipCronet() ? PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET;\n    }\n\n    /**\n     * Bad idea. Faster source is different among devices\n     */\n    private boolean isFasterDataSourceEnabled() {'''
if needle not in s:
    raise SystemExit('compatibility anchor not found')
s = s.replace(needle, helper, 1)
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
