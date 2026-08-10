#!/usr/bin/env python3
"""Copy the newest BaselineProfileRule HRF output into smarttubetv/src/main/baseline-prof.txt."""
from pathlib import Path
import shutil
import sys

root = Path(__file__).resolve().parents[1]
build = root / "mobilebenchmark" / "build" / "outputs"
candidates = sorted(
    build.rglob("*baseline-prof.txt"),
    key=lambda p: p.stat().st_mtime,
    reverse=True,
) if build.exists() else []
if not candidates:
    print("No generated *baseline-prof.txt found under mobilebenchmark/build/outputs", file=sys.stderr)
    print("Run :mobilebenchmark:connectedStmobileBenchmarkAndroidTest first.", file=sys.stderr)
    sys.exit(2)
source = candidates[0]
target = root / "smarttubetv" / "src" / "main" / "baseline-prof.txt"
shutil.copy2(source, target)
print(f"Installed Baseline Profile:\n  {source}\n-> {target}")
