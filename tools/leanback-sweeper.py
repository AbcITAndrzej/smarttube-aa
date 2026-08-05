#!/usr/bin/env python3
"""Leanback Sweeper for SmartTube Mobile.

The tool is deliberately conservative. It removes only imports proven unused by a
lexical source analysis. Imports whose symbols are still referenced are retained
and annotated for manual migration. Gradle dependencies are removed in ``safe``
mode only when no matching source/XML references remain after the planned source
changes.

Typical usage::

    python tools/leanback-sweeper.py .
    python tools/leanback-sweeper.py . --apply
    python tools/leanback-sweeper.py . --apply --remove-dependencies safe
    python tools/leanback-sweeper.py . --restore latest

No third-party Python packages are required.
"""

from __future__ import print_function

import argparse
import datetime as _dt
import difflib
import hashlib
import json
import os
import re
import shutil
import stat
import sys
import tempfile
import uuid
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

TOOL_VERSION = "1.0.0"
TODO_MARKER = "TODO: Oczyszczone z Leanback [leanback-sweeper]"
SOURCE_SUFFIXES = {".java", ".kt"}
XML_SUFFIXES = {".xml"}
GRADLE_FILE_NAMES = {"build.gradle", "settings.gradle", "build.gradle.kts", "settings.gradle.kts"}
DEFAULT_EXCLUDED_PARTS = {
    ".git", ".gradle", ".idea", ".leanback-sweeper-backups",
    ".leanback-sweeper-reports", "build", "out", "target", "generated",
    "node_modules", ".cxx", ".externalNativeBuild",
}

LEANBACK_PREFIXES = (
    "androidx.leanback.",
    "com.google.android.exoplayer2.ext.leanback.",
    "com.google.android.exoplayer.ext.leanback.",
)

# A deliberately broad catalogue. It is used only to decide whether a wildcard
# import is obviously still needed. Unknown wildcard imports remain untouched by
# default, which is safer than guessing.
KNOWN_LEANBACK_SYMBOLS = {
    "ArrayObjectAdapter", "BaseCardView", "BaseGridView", "BrowseFrameLayout",
    "BrowseSupportFragment", "ClassPresenterSelector", "DetailsOverviewLogoPresenter",
    "DetailsOverviewRow", "DetailsSupportFragment", "DiffCallback", "FocusHighlight",
    "FullWidthDetailsOverviewRowPresenter", "GuidanceStylist", "GuidedAction",
    "GuidedStepSupportFragment", "HeaderItem", "HorizontalGridView", "ImageCardView",
    "ItemBridgeAdapter", "LeanbackListPreferenceDialogFragmentCompat",
    "LeanbackPlaybackState", "LeanbackPreferenceDialogFragmentCompat",
    "LeanbackPreferenceFragmentCompat", "LeanbackSettingsFragmentCompat",
    "ListRow", "ListRowPresenter", "ObjectAdapter", "OnChildViewHolderSelectedListener",
    "OnItemViewClickedListener", "OnItemViewSelectedListener", "PageRow",
    "PlaybackControlsRow", "PlaybackControlsRowPresenter", "PlaybackGlue",
    "PlaybackSupportFragment", "Presenter", "PresenterSelector", "Row",
    "RowPresenter", "RowsSupportFragment", "SearchSupportFragment", "SectionRow",
    "ShadowOverlayContainer", "SpeechRecognitionCallback", "TitleViewAdapter",
    "VerticalGridPresenter", "VerticalGridSupportFragment", "VerticalGridView",
    "VideoSupportFragment", "PlaybackTransportControlGlue", "PlayerAdapter",
    "LeanbackPlayerAdapter", "PlaybackBaseControlGlue", "PlaybackBannerControlGlue",
    "PlaybackControlsRowPresenter", "PlaybackRowPresenter", "AbstractDetailsDescriptionPresenter",
    "Action", "ControlButtonPresenterSelector", "DetailsParallax", "ParallaxTarget",
}

KNOWN_DEPENDENCIES = {
    "androidx_leanback": (
        "androidx.leanback:leanback",
        "androidx.leanback:leanback-preference",
        "androidx.leanback:leanback-tab",
        "androidx.leanback:leanback-grid",
    ),
    "exoplayer_leanback": (
        "com.google.android.exoplayer:extension-leanback",
        "com.google.android.exoplayer2:extension-leanback",
    ),
}

IMPORT_RE = re.compile(
    r"^(?P<indent>\s*)import\s+(?P<static>static\s+)?"
    r"(?P<qualified>(?:androidx\.leanback|com\.google\.android\.exoplayer2?\.ext\.leanback)"
    r"\.[A-Za-z0-9_.$*]+)"
    r"(?:\s+as\s+(?P<alias>[A-Za-z_][A-Za-z0-9_]*))?\s*;?\s*(?://.*)?$"
)


@dataclass
class ImportFinding:
    path: str
    line: int
    qualified: str
    symbol: str
    wildcard: bool
    used: bool
    action: str
    reason: str
    references: List[str] = field(default_factory=list)


@dataclass
class DependencyFinding:
    path: str
    line: int
    text: str
    family: str
    action: str
    reason: str


@dataclass
class ReferenceFinding:
    path: str
    line: int
    kind: str
    text: str


@dataclass
class FileChange:
    path: str
    changed: bool
    removed_imports: int = 0
    annotations_added: int = 0
    dependencies_removed: int = 0
    sha256_before: str = ""
    sha256_after: str = ""


@dataclass
class ScanResult:
    root: str
    imports: List[ImportFinding] = field(default_factory=list)
    dependencies: List[DependencyFinding] = field(default_factory=list)
    references: List[ReferenceFinding] = field(default_factory=list)
    changes: List[FileChange] = field(default_factory=list)
    skipped: List[Dict[str, str]] = field(default_factory=list)
    modified_text: Dict[str, str] = field(default_factory=dict, repr=False)
    original_text: Dict[str, str] = field(default_factory=dict, repr=False)
    encodings: Dict[str, str] = field(default_factory=dict, repr=False)
    boms: Dict[str, bool] = field(default_factory=dict, repr=False)

    def remaining_count(self) -> int:
        hard_imports = sum(1 for item in self.imports if item.action.startswith("keep"))
        hard_dependencies = sum(1 for item in self.dependencies if item.action.startswith("keep"))
        return hard_imports + hard_dependencies + len(self.references)


@dataclass
class Options:
    root: Path
    apply: bool
    annotate: bool
    remove_imports: bool
    remove_dependencies: str
    allow_wildcard_removal: bool
    include_paths: List[Path]
    exclude_parts: Set[str]
    max_file_size: int
    backup: bool
    report_dir: Path
    fail_on_remaining: bool


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def utc_timestamp() -> str:
    return _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def file_timestamp() -> str:
    return _dt.datetime.now(_dt.timezone.utc).strftime("%Y%m%d-%H%M%S")


def read_utf8(path: Path) -> Tuple[str, bool]:
    raw = path.read_bytes()
    has_bom = raw.startswith(b"\xef\xbb\xbf")
    if has_bom:
        raw = raw[3:]
    return raw.decode("utf-8"), has_bom


def encode_utf8(text: str, bom: bool) -> bytes:
    payload = text.encode("utf-8")
    return (b"\xef\xbb\xbf" + payload) if bom else payload


def is_candidate(path: Path) -> bool:
    return path.suffix.lower() in SOURCE_SUFFIXES or path.suffix.lower() in XML_SUFFIXES or path.name in GRADLE_FILE_NAMES


def is_excluded(path: Path, root: Path, excluded_parts: Set[str]) -> bool:
    try:
        rel = path.relative_to(root)
    except ValueError:
        return True
    return any(part in excluded_parts for part in rel.parts)


def collect_files(options: Options) -> List[Path]:
    roots = options.include_paths or [options.root]
    collected: List[Path] = []
    seen: Set[Path] = set()
    for scan_root in roots:
        scan_root = scan_root if scan_root.is_absolute() else options.root / scan_root
        if not scan_root.exists():
            continue
        candidates = [scan_root] if scan_root.is_file() else scan_root.rglob("*")
        for path in candidates:
            if not path.is_file() or not is_candidate(path):
                continue
            resolved = path.resolve()
            if resolved in seen or is_excluded(resolved, options.root, options.exclude_parts):
                continue
            seen.add(resolved)
            collected.append(resolved)
    return sorted(collected)


def remove_old_sweeper_comments(text: str) -> str:
    lines = text.splitlines(keepends=True)
    return "".join(line for line in lines if TODO_MARKER not in line)


def mask_non_code(text: str) -> str:
    """Replace comments and literals with spaces while preserving newlines.

    This is not a Java/Kotlin parser. It is a conservative lexical masker used
    only for identifier presence checks. It understands Java/Kotlin comments,
    quoted strings, chars, Kotlin triple-quoted strings and escapes.
    """
    chars = list(text)
    out = list(text)
    i = 0
    state = "code"
    while i < len(chars):
        ch = chars[i]
        nxt = chars[i + 1] if i + 1 < len(chars) else ""
        tri = "".join(chars[i:i + 3])
        if state == "code":
            if ch == "/" and nxt == "/":
                out[i] = out[i + 1] = " "
                i += 2
                state = "line_comment"
                continue
            if ch == "/" and nxt == "*":
                out[i] = out[i + 1] = " "
                i += 2
                state = "block_comment"
                continue
            if tri == '"""':
                out[i:i + 3] = [" ", " ", " "]
                i += 3
                state = "triple_string"
                continue
            if ch == '"':
                out[i] = " "
                i += 1
                state = "string"
                continue
            if ch == "'":
                out[i] = " "
                i += 1
                state = "char"
                continue
            i += 1
            continue
        if state == "line_comment":
            if ch == "\n":
                state = "code"
            else:
                out[i] = " "
            i += 1
            continue
        if state == "block_comment":
            if ch == "*" and nxt == "/":
                out[i] = out[i + 1] = " "
                i += 2
                state = "code"
            else:
                if ch != "\n":
                    out[i] = " "
                i += 1
            continue
        if state == "triple_string":
            if tri == '"""':
                out[i:i + 3] = [" ", " ", " "]
                i += 3
                state = "code"
            else:
                if ch != "\n":
                    out[i] = " "
                i += 1
            continue
        if state in ("string", "char"):
            if ch == "\\":
                out[i] = " "
                if i + 1 < len(chars):
                    if chars[i + 1] != "\n":
                        out[i + 1] = " "
                    i += 2
                else:
                    i += 1
                continue
            closing = '"' if state == "string" else "'"
            if ch == closing:
                out[i] = " "
                i += 1
                state = "code"
            else:
                if ch != "\n":
                    out[i] = " "
                i += 1
            continue
    return "".join(out)


def code_without_import_lines(text: str) -> str:
    lines = text.splitlines(keepends=True)
    cleaned = []
    for line in lines:
        if IMPORT_RE.match(line.rstrip("\r\n")):
            cleaned.append("\n" if line.endswith("\n") else "")
        else:
            cleaned.append(line)
    return mask_non_code("".join(cleaned))


def identifier_references(code: str, symbol: str) -> List[str]:
    pattern = re.compile(r"\b" + re.escape(symbol) + r"\b")
    refs = []
    for line_no, line in enumerate(code.splitlines(), 1):
        if pattern.search(line):
            refs.append("line %d" % line_no)
            if len(refs) >= 5:
                break
    return refs


def wildcard_references(code: str) -> List[str]:
    refs: List[str] = []
    for prefix in LEANBACK_PREFIXES:
        if prefix in code:
            refs.append(prefix + "…")
    for symbol in sorted(KNOWN_LEANBACK_SYMBOLS):
        if re.search(r"\b" + re.escape(symbol) + r"\b", code):
            refs.append(symbol)
            if len(refs) >= 12:
                break
    return refs


def relative(path: Path, root: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return str(path)


def preferred_newline(text: str) -> str:
    return "\r\n" if text.count("\r\n") > text.count("\n") / 2 else "\n"


def todo_comment(indent: str, message: str, newline: str = "\n") -> str:
    return "%s// %s: %s%s" % (indent, TODO_MARKER, message, newline)


def process_source(path: Path, text: str, options: Options, result: ScanResult) -> str:
    rel = relative(path, options.root)
    text = remove_old_sweeper_comments(text)
    newline = preferred_newline(text)
    lines = text.splitlines(keepends=True)
    code = code_without_import_lines(text)
    remove_lines: Set[int] = set()
    annotations: Dict[int, str] = {}
    import_findings: List[ImportFinding] = []

    for idx, line in enumerate(lines):
        match = IMPORT_RE.match(line.rstrip("\r\n"))
        if not match:
            continue
        qualified = match.group("qualified")
        wildcard = qualified.endswith(".*")
        alias = match.group("alias")
        symbol = alias or qualified.rsplit(".", 1)[-1]
        refs = wildcard_references(code) if wildcard else identifier_references(code, symbol)
        used = bool(refs)
        if wildcard and not options.allow_wildcard_removal:
            action = "keep-manual"
            reason = "Wildcard import is never removed automatically without --allow-wildcard-removal."
        elif used:
            action = "keep-used"
            reason = "Imported symbol is still referenced outside comments and literals."
        elif options.remove_imports:
            action = "remove-unused"
            reason = "No lexical reference to the imported symbol was found."
            remove_lines.add(idx)
        else:
            action = "keep-disabled"
            reason = "Import removal was disabled by --no-remove-imports."

        finding = ImportFinding(
            path=rel,
            line=idx + 1,
            qualified=qualified,
            symbol=symbol,
            wildcard=wildcard,
            used=used,
            action=action,
            reason=reason,
            references=refs,
        )
        import_findings.append(finding)
        if action.startswith("keep") and options.annotate:
            if wildcard:
                msg = "%s pozostawiono: import wieloznaczny wymaga ręcznej migracji." % qualified
            elif used:
                msg = "%s nadal jest używany (%s); zastąp odpowiednikiem mobilnym przed usunięciem importu." % (
                    qualified, ", ".join(refs) if refs else "referencja wykryta")
            else:
                msg = "%s pozostawiono, ponieważ automatyczne usuwanie importów jest wyłączone." % qualified
            annotations[idx] = todo_comment(match.group("indent") or "", msg, newline)

    result.imports.extend(import_findings)

    # Fully qualified Leanback references can exist without imports. Flag the
    # first code occurrence. It remains a manual migration point.
    masked_lines = mask_non_code(text).splitlines()
    import_line_numbers = {item.line for item in import_findings}
    for line_no, masked_line in enumerate(masked_lines, 1):
        if line_no in import_line_numbers:
            continue
        hit = next((prefix for prefix in LEANBACK_PREFIXES if prefix in masked_line), None)
        if hit:
            original = lines[line_no - 1].strip() if line_no - 1 < len(lines) else hit
            result.references.append(ReferenceFinding(rel, line_no, "source-qualified", original[:300]))
            if options.annotate:
                indent = re.match(r"\s*", lines[line_no - 1]).group(0)
                annotations.setdefault(line_no - 1, todo_comment(
                    indent,
                    "referencja kwalifikowana %s… wymaga zastąpienia mobilnym API." % hit.rstrip("."),
                    newline,
                ))
            break

    output: List[str] = []
    removed = 0
    added = 0
    for idx, line in enumerate(lines):
        if idx in annotations:
            output.append(annotations[idx])
            added += 1
        if idx in remove_lines:
            removed += 1
            continue
        output.append(line)
    new_text = "".join(output)
    result.changes.append(FileChange(path=rel, changed=(new_text != text), removed_imports=removed, annotations_added=added))
    return new_text


def dependency_family(line: str) -> Optional[str]:
    lowered = line.lower()
    for family, coordinates in KNOWN_DEPENDENCIES.items():
        if any(coord.lower() in lowered for coord in coordinates):
            return family
    if "leanback" in lowered:
        return "unknown_leanback"
    return None


def source_reference_counts(projected: Dict[str, str], xml_texts: Dict[str, str]) -> Dict[str, int]:
    counts = {"androidx_leanback": 0, "exoplayer_leanback": 0}
    for text in list(projected.values()) + list(xml_texts.values()):
        masked = mask_non_code(text) if not text.lstrip().startswith("<") else text
        if "androidx.leanback." in masked or "androidx.leanback:" in masked:
            counts["androidx_leanback"] += 1
        if "com.google.android.exoplayer2.ext.leanback." in masked or "com.google.android.exoplayer.ext.leanback." in masked:
            counts["exoplayer_leanback"] += 1
    return counts


def process_gradle(path: Path, text: str, options: Options, result: ScanResult, ref_counts: Dict[str, int]) -> str:
    rel = relative(path, options.root)
    text = remove_old_sweeper_comments(text)
    newline = preferred_newline(text)
    lines = text.splitlines(keepends=True)
    remove_lines: Set[int] = set()
    annotations: Dict[int, str] = {}
    findings: List[DependencyFinding] = []

    for idx, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
            continue
        family = dependency_family(line)
        if not family:
            continue
        if family == "unknown_leanback":
            action = "keep-unknown"
            reason = "Line mentions Leanback but is not a recognized dependency declaration."
        elif options.remove_dependencies == "never":
            action = "keep-disabled"
            reason = "Dependency removal was disabled."
        elif options.remove_dependencies == "force":
            action = "remove-force"
            reason = "Forced dependency removal requested by the operator."
            remove_lines.add(idx)
        elif ref_counts.get(family, 0) == 0:
            action = "remove-safe"
            reason = "No matching source or XML references remain in the scanned project."
            remove_lines.add(idx)
        else:
            action = "keep-required"
            reason = "%d matching source/XML file(s) still reference this Leanback family." % ref_counts.get(family, 0)

        findings.append(DependencyFinding(rel, idx + 1, stripped[:500], family, action, reason))
        if action.startswith("keep") and options.annotate:
            indent = re.match(r"\s*", line).group(0)
            annotations[idx] = todo_comment(indent, "%s — %s" % (stripped[:180], reason), newline)

    result.dependencies.extend(findings)
    output: List[str] = []
    removed = 0
    added = 0
    for idx, line in enumerate(lines):
        if idx in annotations:
            output.append(annotations[idx])
            added += 1
        if idx in remove_lines:
            removed += 1
            continue
        output.append(line)
    new_text = "".join(output)
    result.changes.append(FileChange(path=rel, changed=(new_text != text), annotations_added=added, dependencies_removed=removed))
    return new_text


def scan_xml(path: Path, text: str, options: Options, result: ScanResult) -> None:
    rel = relative(path, options.root)
    for line_no, line in enumerate(text.splitlines(), 1):
        lowered = line.lower()
        if "androidx.leanback" in line or "android.software.leanback" in line or "leanback" in lowered:
            result.references.append(ReferenceFinding(rel, line_no, "xml-manifest", line.strip()[:500]))


def load_candidates(options: Options, result: ScanResult) -> Dict[Path, Tuple[str, bool]]:
    loaded: Dict[Path, Tuple[str, bool]] = {}
    for path in collect_files(options):
        try:
            size = path.stat().st_size
            if size > options.max_file_size:
                result.skipped.append({"path": relative(path, options.root), "reason": "file exceeds max size"})
                continue
            text, bom = read_utf8(path)
            loaded[path] = (text, bom)
        except (UnicodeDecodeError, OSError) as exc:
            result.skipped.append({"path": relative(path, options.root), "reason": str(exc)})
    return loaded


def analyze(options: Options) -> ScanResult:
    result = ScanResult(root=str(options.root))
    loaded = load_candidates(options, result)
    projected_sources: Dict[str, str] = {}
    xml_texts: Dict[str, str] = {}
    gradle_files: List[Tuple[Path, str, bool]] = []

    for path, (original, bom) in loaded.items():
        rel = relative(path, options.root)
        result.original_text[rel] = original
        result.boms[rel] = bom
        result.encodings[rel] = "utf-8-sig" if bom else "utf-8"
        if path.suffix.lower() in SOURCE_SUFFIXES:
            projected = process_source(path, original, options, result)
            projected_sources[rel] = projected
            result.modified_text[rel] = projected
        elif path.suffix.lower() in XML_SUFFIXES:
            scan_xml(path, original, options, result)
            xml_texts[rel] = original
            result.modified_text[rel] = original
            result.changes.append(FileChange(path=rel, changed=False))
        elif path.name in GRADLE_FILE_NAMES:
            gradle_files.append((path, original, bom))

    counts = source_reference_counts(projected_sources, xml_texts)
    for path, original, _bom in gradle_files:
        rel = relative(path, options.root)
        projected = process_gradle(path, original, options, result, counts)
        result.modified_text[rel] = projected

    # Populate hashes after all projected changes are known.
    changes_by_path: Dict[str, FileChange] = {}
    for change in result.changes:
        existing = changes_by_path.get(change.path)
        if existing is None:
            changes_by_path[change.path] = change
        else:
            existing.changed = existing.changed or change.changed
            existing.removed_imports += change.removed_imports
            existing.annotations_added += change.annotations_added
            existing.dependencies_removed += change.dependencies_removed
    result.changes = sorted(changes_by_path.values(), key=lambda item: item.path)
    for change in result.changes:
        original = result.original_text.get(change.path, "")
        modified = result.modified_text.get(change.path, original)
        bom = result.boms.get(change.path, False)
        change.sha256_before = sha256_bytes(encode_utf8(original, bom))
        change.sha256_after = sha256_bytes(encode_utf8(modified, bom))
        change.changed = original != modified
    return result


def create_backup(options: Options, result: ScanResult, changed_paths: List[str]) -> Path:
    backup_root = options.root / ".leanback-sweeper-backups" / (file_timestamp() + "-" + uuid.uuid4().hex[:8])
    files_root = backup_root / "files"
    files_root.mkdir(parents=True, exist_ok=False)
    manifest_files = []
    for rel in changed_paths:
        src = options.root / rel
        dst = files_root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(str(src), str(dst))
        manifest_files.append({
            "path": rel,
            "sha256_before": sha256_bytes(src.read_bytes()),
            "sha256_planned": sha256_bytes(encode_utf8(result.modified_text[rel], result.boms.get(rel, False))),
        })
    manifest = {
        "schema": 1,
        "tool": "leanback-sweeper",
        "tool_version": TOOL_VERSION,
        "created_at": utc_timestamp(),
        "project_root": str(options.root),
        "files": manifest_files,
    }
    (backup_root / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return backup_root


def atomic_write(path: Path, data: bytes) -> None:
    mode = stat.S_IMODE(path.stat().st_mode)
    fd, temp_name = tempfile.mkstemp(prefix=path.name + ".sweeper-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.chmod(temp_name, mode)
        os.replace(temp_name, str(path))
    except Exception:
        try:
            os.unlink(temp_name)
        except OSError:
            pass
        raise


def restore_backup(root: Path, selector: str) -> Path:
    backups_root = root / ".leanback-sweeper-backups"
    if selector == "latest":
        candidates = sorted((p for p in backups_root.iterdir() if p.is_dir()), reverse=True) if backups_root.exists() else []
        if not candidates:
            raise RuntimeError("No Leanback Sweeper backups found.")
        backup = candidates[0]
    else:
        backup = Path(selector)
        if not backup.is_absolute():
            candidate = backups_root / selector
            backup = candidate if candidate.exists() else (root / selector)
        backup = backup.resolve()
    manifest_path = backup / "manifest.json"
    if not manifest_path.exists():
        raise RuntimeError("Backup manifest not found: %s" % manifest_path)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for entry in manifest.get("files", []):
        rel = entry["path"]
        src = backup / "files" / rel
        dst = root / rel
        if not src.exists():
            raise RuntimeError("Backup file missing: %s" % src)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(str(src), str(dst))
    return backup


def apply_changes(options: Options, result: ScanResult) -> Optional[Path]:
    changed = [item.path for item in result.changes if item.changed]
    if not changed:
        return None
    backup = create_backup(options, result, changed) if options.backup else None
    written: List[str] = []
    try:
        for rel in changed:
            path = options.root / rel
            atomic_write(path, encode_utf8(result.modified_text[rel], result.boms.get(rel, False)))
            written.append(rel)
    except Exception:
        if backup is not None:
            restore_backup(options.root, str(backup))
        raise
    return backup


def build_diff(result: ScanResult) -> str:
    chunks: List[str] = []
    for change in result.changes:
        if not change.changed:
            continue
        before = result.original_text[change.path].splitlines(keepends=True)
        after = result.modified_text[change.path].splitlines(keepends=True)
        chunks.extend(difflib.unified_diff(
            before, after,
            fromfile="a/" + change.path,
            tofile="b/" + change.path,
        ))
    return "".join(chunks)


def serializable_result(result: ScanResult, applied: bool, backup: Optional[Path]) -> Dict[str, object]:
    return {
        "schema": 1,
        "tool": "leanback-sweeper",
        "tool_version": TOOL_VERSION,
        "generated_at": utc_timestamp(),
        "root": result.root,
        "applied": applied,
        "backup": str(backup) if backup else None,
        "summary": {
            "imports_total": len(result.imports),
            "imports_removed": sum(1 for item in result.imports if item.action.startswith("remove")),
            "imports_remaining": sum(1 for item in result.imports if item.action.startswith("keep")),
            "dependencies_total": len(result.dependencies),
            "dependencies_removed": sum(1 for item in result.dependencies if item.action.startswith("remove")),
            "dependencies_remaining": sum(1 for item in result.dependencies if item.action.startswith("keep")),
            "other_references": len(result.references),
            "changed_files": sum(1 for item in result.changes if item.changed),
            "remaining_migration_points": result.remaining_count(),
            "skipped_files": len(result.skipped),
        },
        "imports": [asdict(item) for item in result.imports],
        "dependencies": [asdict(item) for item in result.dependencies],
        "references": [asdict(item) for item in result.references],
        "changes": [asdict(item) for item in result.changes],
        "skipped": result.skipped,
    }


def markdown_report(payload: Dict[str, object]) -> str:
    summary = payload["summary"]
    lines = [
        "# Leanback Sweeper report",
        "",
        "- Generated: `%s`" % payload["generated_at"],
        "- Root: `%s`" % payload["root"],
        "- Mode: **%s**" % ("APPLY" if payload["applied"] else "DRY-RUN"),
        "- Backup: `%s`" % (payload["backup"] or "not created"),
        "",
        "## Summary",
        "",
        "| Metric | Count |",
        "|---|---:|",
    ]
    for key, value in summary.items():
        lines.append("| `%s` | %s |" % (key, value))
    lines.extend(["", "## Imports", ""])
    imports = payload["imports"]
    if not imports:
        lines.append("No Leanback imports found.")
    else:
        lines.extend(["| File | Line | Import | Action | Reason |", "|---|---:|---|---|---|"])
        for item in imports:
            lines.append("| `%s` | %s | `%s` | `%s` | %s |" % (
                item["path"], item["line"], item["qualified"], item["action"], str(item["reason"]).replace("|", "\\|")))
    lines.extend(["", "## Gradle dependencies", ""])
    deps = payload["dependencies"]
    if not deps:
        lines.append("No Leanback-related Gradle lines found.")
    else:
        lines.extend(["| File | Line | Family | Action | Text |", "|---|---:|---|---|---|"])
        for item in deps:
            lines.append("| `%s` | %s | `%s` | `%s` | `%s` |" % (
                item["path"], item["line"], item["family"], item["action"], str(item["text"]).replace("|", "\\|")))
    lines.extend(["", "## Remaining references", ""])
    refs = payload["references"]
    if not refs:
        lines.append("No additional Leanback references found.")
    else:
        for item in refs:
            lines.append("- `%s:%s` (`%s`): `%s`" % (item["path"], item["line"], item["kind"], item["text"]))
    lines.extend(["", "## Changed files", ""])
    changes = [item for item in payload["changes"] if item["changed"]]
    if not changes:
        lines.append("No file changes planned.")
    else:
        for item in changes:
            lines.append("- `%s`: imports -%s, dependencies -%s, TODO +%s" % (
                item["path"], item["removed_imports"], item["dependencies_removed"], item["annotations_added"]))
    lines.extend([
        "",
        "## Safety note",
        "",
        "The sweeper removes only imports proven unused by lexical analysis. Files with live Leanback symbols remain buildable and are marked for manual migration. Always review `changes.diff` and compile the affected variants before deleting the backup.",
        "",
    ])
    return "\n".join(lines)


def write_reports(options: Options, result: ScanResult, applied: bool, backup: Optional[Path]) -> Tuple[Path, Path, Path]:
    options.report_dir.mkdir(parents=True, exist_ok=True)
    payload = serializable_result(result, applied, backup)
    json_path = options.report_dir / "report.json"
    md_path = options.report_dir / "report.md"
    diff_path = options.report_dir / "changes.diff"
    json_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    md_path.write_text(markdown_report(payload), encoding="utf-8")
    diff_path.write_text(build_diff(result), encoding="utf-8")
    return json_path, md_path, diff_path


def print_summary(result: ScanResult, applied: bool, backup: Optional[Path], report_paths: Tuple[Path, Path, Path]) -> None:
    removed_imports = sum(1 for item in result.imports if item.action.startswith("remove"))
    remaining_imports = sum(1 for item in result.imports if item.action.startswith("keep"))
    removed_deps = sum(1 for item in result.dependencies if item.action.startswith("remove"))
    remaining_deps = sum(1 for item in result.dependencies if item.action.startswith("keep"))
    changed = sum(1 for item in result.changes if item.changed)
    print("Leanback Sweeper %s" % TOOL_VERSION)
    print("Mode: %s" % ("APPLY" if applied else "DRY-RUN"))
    print("Changed files: %d" % changed)
    print("Imports planned/removed: %d; remaining: %d" % (removed_imports, remaining_imports))
    print("Dependencies planned/removed: %d; remaining: %d" % (removed_deps, remaining_deps))
    print("Additional references: %d" % len(result.references))
    if backup:
        print("Backup: %s" % backup)
    print("JSON report: %s" % report_paths[0])
    print("Markdown report: %s" % report_paths[1])
    print("Diff: %s" % report_paths[2])


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Safely audit and gradually remove AndroidX Leanback from an Android project.")
    parser.add_argument("root", nargs="?", default=".", help="Project root (default: current directory).")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true", help="Write planned changes. Default is dry-run.")
    mode.add_argument("--restore", metavar="BACKUP", help="Restore a backup path/name or 'latest'.")
    parser.add_argument("--include", action="append", default=[], help="Relative path to scan; repeatable. Default: whole project.")
    parser.add_argument("--exclude", action="append", default=[], help="Additional path component to exclude; repeatable.")
    parser.add_argument("--no-annotate", action="store_true", help="Do not add TODO comments to unresolved migration points.")
    parser.add_argument("--no-remove-imports", action="store_true", help="Audit imports without removing unused ones.")
    parser.add_argument("--allow-wildcard-removal", action="store_true", help="Allow removal of an unused wildcard import. Disabled by default.")
    parser.add_argument("--remove-dependencies", choices=("safe", "never", "force"), default="safe",
                        help="Gradle dependency policy (default: safe).")
    parser.add_argument("--no-backup", action="store_true", help="Apply without a backup. Not recommended.")
    parser.add_argument("--report-dir", help="Report directory. Default: .leanback-sweeper-reports/<timestamp>.")
    parser.add_argument("--max-file-size", type=int, default=4 * 1024 * 1024, help="Maximum scanned file size in bytes.")
    parser.add_argument("--fail-on-remaining", action="store_true", help="Exit 3 when manual migration points remain.")
    parser.add_argument("--version", action="version", version="%(prog)s " + TOOL_VERSION)
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    root = Path(args.root).resolve()
    if not root.exists() or not root.is_dir():
        print("ERROR: project root does not exist or is not a directory: %s" % root, file=sys.stderr)
        return 2
    if args.restore:
        try:
            restored = restore_backup(root, args.restore)
            print("Restored Leanback Sweeper backup: %s" % restored)
            return 0
        except Exception as exc:
            print("ERROR: %s" % exc, file=sys.stderr)
            return 2

    report_dir = Path(args.report_dir).resolve() if args.report_dir else root / ".leanback-sweeper-reports" / file_timestamp()
    options = Options(
        root=root,
        apply=args.apply,
        annotate=not args.no_annotate,
        remove_imports=not args.no_remove_imports,
        remove_dependencies=args.remove_dependencies,
        allow_wildcard_removal=args.allow_wildcard_removal,
        include_paths=[Path(item) for item in args.include],
        exclude_parts=set(DEFAULT_EXCLUDED_PARTS).union(args.exclude),
        max_file_size=max(1, args.max_file_size),
        backup=not args.no_backup,
        report_dir=report_dir,
        fail_on_remaining=args.fail_on_remaining,
    )

    try:
        result = analyze(options)
        backup = apply_changes(options, result) if options.apply else None
        reports = write_reports(options, result, options.apply, backup)
        print_summary(result, options.apply, backup, reports)
        if options.fail_on_remaining and result.remaining_count() > 0:
            return 3
        return 0
    except Exception as exc:
        print("ERROR: %s" % exc, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
