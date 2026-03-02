#!/usr/bin/env python3
"""Update CHANGELOG.md in Keep a Changelog format based on git commits.

Modes:
- auto (default): release mode on semver tag push (vX.Y.Z), otherwise unreleased mode
- unreleased: refreshes only the [Unreleased] section from commits since latest semver tag
- release: creates/replaces section for provided --release-version and resets [Unreleased]
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import re
import subprocess
from collections import OrderedDict
from pathlib import Path

SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$")
HEADER_RE = re.compile(r"^## \[([^\]]+)\](?: - (\d{4}-\d{2}-\d{2}))?\s*$")
CC_RE = re.compile(
    r"^(feat|fix|perf|refactor|docs|chore|build|ci|test|style)(?:\([^)]+\))?!?:\s*(.+)$",
    re.IGNORECASE,
)
JIRA_RE = re.compile(r"\b(RDFA-\d+)\b", re.IGNORECASE)
GH_RE = re.compile(r"(?:\(#(\d+)\)|#(\d+)\b|\bGH-(\d+)\b)", re.IGNORECASE)
REMOVED_RE = re.compile(r"\b(remove|removed|delete|deleted|drop|dropped|deprecat(?:e|ed|ion))\b", re.IGNORECASE)

KEEP_A_CHANGELOG_PREAMBLE = """# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)."""
CATEGORY_ORDER = ("Added", "Changed", "Fixed", "Removed")


def run_git(args: list[str], cwd: Path) -> str:
    res = subprocess.run(["git", *args], cwd=cwd, text=True, capture_output=True)
    if res.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {res.stderr.strip()}")
    return res.stdout.strip()


def validate_semver(value: str, name: str) -> str:
    if not SEMVER_RE.match(value):
        raise ValueError(f"{name} must be SemVer, got: {value}")
    return value


def latest_semver_tag(repo_root: Path) -> str | None:
    out = run_git(["tag", "--list", "v*", "--sort=-v:refname"], repo_root)
    for tag in out.splitlines():
        version = tag[1:] if tag.startswith("v") else tag
        if SEMVER_RE.match(version):
            return tag
    return None


def previous_semver_tag(repo_root: Path, current_tag: str) -> str | None:
    out = run_git(["tag", "--list", "v*", "--sort=-v:refname"], repo_root)
    tags = [t.strip() for t in out.splitlines() if t.strip()]
    filtered = []
    for tag in tags:
        version = tag[1:] if tag.startswith("v") else tag
        if SEMVER_RE.match(version):
            filtered.append(tag)
    for tag in filtered:
        if tag != current_tag:
            return tag
    return None


def commit_subjects(repo_root: Path, revision_range: str | None) -> list[str]:
    args = ["log", "--pretty=%s"]
    if revision_range:
        args.append(revision_range)
    out = run_git(args, repo_root)
    subjects = [line.strip() for line in out.splitlines() if line.strip()]

    cleaned: list[str] = []
    seen: set[str] = set()
    for s in subjects:
        if s.startswith("Merge "):
            continue
        if s not in seen:
            seen.add(s)
            cleaned.append(s)
    return cleaned


def classify_subjects(subjects: list[str]) -> OrderedDict[str, list[str]]:
    groups: OrderedDict[str, list[str]] = OrderedDict((k, []) for k in CATEGORY_ORDER)

    for subject in subjects:
        m = CC_RE.match(subject)
        if m:
            kind = m.group(1).lower()
            msg = m.group(2).strip()
            category = classify_category(kind, msg)
            groups[category].append(format_entry(subject, msg))
        else:
            category = classify_category("", subject)
            groups[category].append(format_entry(subject, subject))

    return groups


def classify_category(kind: str, message: str) -> str:
    if kind == "feat":
        return "Added"
    if kind == "fix":
        return "Fixed"
    if REMOVED_RE.search(message):
        return "Removed"
    return "Changed"


def format_entry(subject: str, message: str) -> str:
    jira = extract_jira_id(subject)
    gh = extract_gh_id(subject)
    description = normalize_message(message)
    line = f"{jira}: {description}" if jira else description
    if gh:
        line = f"{line} ({gh})"
    return line


def extract_jira_id(text: str) -> str | None:
    match = JIRA_RE.search(text)
    if not match:
        return None
    return match.group(1).upper()


def extract_gh_id(text: str) -> str | None:
    match = GH_RE.search(text)
    if not match:
        return None
    digits = next((g for g in match.groups() if g), "")
    return f"GH-{digits}" if digits else None


def normalize_message(message: str) -> str:
    msg = message
    msg = JIRA_RE.sub("", msg)
    msg = re.sub(r"\(#\d+\)", "", msg)
    msg = re.sub(r"\bGH-\d+\b", "", msg, flags=re.IGNORECASE)
    msg = re.sub(r"#\d+\b", "", msg)
    msg = re.sub(r"\((?:\s|[,;:/-])*\)", "", msg)
    msg = re.sub(r"\(\s*\)", "", msg)
    msg = re.sub(r"\[\s*\]", "", msg)
    msg = re.sub(r"\s+", " ", msg)
    msg = re.sub(r",\s*,", ",", msg)
    msg = re.sub(r"\(\s*,\s*", "(", msg)
    msg = re.sub(r"\s+([,;:.!?])", r"\1", msg)
    msg = msg.strip(" \t-_,;:.")
    if not msg:
        return "No description provided"
    return msg[0].upper() + msg[1:]


def canonical_entry_key(entry: str) -> str:
    msg = entry.strip()
    msg = re.sub(r"^RDFA-\d+:\s*", "", msg, flags=re.IGNORECASE)
    msg = re.sub(r"\s*\(GH-\d+\)\s*$", "", msg, flags=re.IGNORECASE)
    msg = re.sub(r"\s+", " ", msg).strip().lower()
    return msg


def parse_grouped_section(body: str) -> OrderedDict[str, list[str]]:
    groups: OrderedDict[str, list[str]] = OrderedDict((k, []) for k in CATEGORY_ORDER)
    current: str | None = None

    for raw_line in body.splitlines():
        line = raw_line.strip()
        category_match = re.match(r"^### (Added|Changed|Fixed|Removed)$", line)
        if category_match:
            current = category_match.group(1)
            continue

        if current and line.startswith("- "):
            entry = sanitize_existing_entry(line[2:].strip())
            if entry:
                groups[current].append(entry)

    return groups


def sanitize_existing_entry(entry: str) -> str:
    # Backward-compatible cleanup for old placeholder IDs.
    cleaned = entry.strip()
    cleaned = re.sub(r"^RDFA-000:\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*\(GH-000\)\s*$", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def merge_grouped_sections(
    existing: OrderedDict[str, list[str]],
    generated: OrderedDict[str, list[str]],
) -> OrderedDict[str, list[str]]:
    merged: OrderedDict[str, list[str]] = OrderedDict((k, []) for k in CATEGORY_ORDER)
    seen_keys: set[str] = set()

    for category in CATEGORY_ORDER:
        for entry in existing.get(category, []):
            key = canonical_entry_key(entry)
            if key:
                seen_keys.add(key)
            merged[category].append(entry)

    for category in CATEGORY_ORDER:
        for entry in generated.get(category, []):
            key = canonical_entry_key(entry)
            if key and key in seen_keys:
                continue
            if key:
                seen_keys.add(key)
            merged[category].append(entry)

    return merged


def render_section(title: str, date: str | None, grouped: OrderedDict[str, list[str]], empty_note: str | None = None) -> str:
    header = f"## [{title}]" + (f" - {date}" if date else "")
    chunks = [header, ""]

    has_entries = False
    for group_name in CATEGORY_ORDER:
        entries = grouped.get(group_name, [])
        if not entries:
            continue
        has_entries = True
        chunks.append(f"### {group_name}")
        chunks.append("")
        for entry in entries:
            chunks.append(f"- {entry}")
        chunks.append("")

    if not has_entries:
        chunks.append("### Changed")
        chunks.append("")
        chunks.append(f"- {empty_note or '_No notable changes yet._'}")
        chunks.append("")

    return "\n".join(chunks).rstrip() + "\n"


def parse_changelog_sections(content: str) -> tuple[str, list[tuple[str, str | None, str]]]:
    lines = content.splitlines()

    headers: list[tuple[int, str, str | None]] = []
    for i, line in enumerate(lines):
        m = HEADER_RE.match(line)
        if m:
            headers.append((i, m.group(1), m.group(2)))

    if not headers:
        return KEEP_A_CHANGELOG_PREAMBLE + "\n", []

    preamble = "\n".join(lines[: headers[0][0]]).rstrip() + "\n"

    sections: list[tuple[str, str | None, str]] = []
    for idx, (start, name, date) in enumerate(headers):
        end = headers[idx + 1][0] if idx + 1 < len(headers) else len(lines)
        body = "\n".join(lines[start + 1 : end]).strip("\n")
        sections.append((name, date, body))

    return preamble, sections


def build_unreleased_from_git(repo_root: Path) -> OrderedDict[str, list[str]]:
    latest_tag = latest_semver_tag(repo_root)
    revision_range = f"{latest_tag}..HEAD" if latest_tag else None
    subjects = commit_subjects(repo_root, revision_range)
    return classify_subjects(subjects)


def build_release_from_git(repo_root: Path, current_tag: str) -> OrderedDict[str, list[str]]:
    prev_tag = previous_semver_tag(repo_root, current_tag)
    revision_range = f"{prev_tag}..{current_tag}" if prev_tag else current_tag
    subjects = commit_subjects(repo_root, revision_range)
    return classify_subjects(subjects)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["auto", "unreleased", "release"], default="auto")
    parser.add_argument("--release-version", default="")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    changelog_path = repo_root / "CHANGELOG.md"
    version_path = repo_root / "VERSION"

    version = validate_semver(version_path.read_text(encoding="utf-8").strip(), "VERSION")

    mode = args.mode
    if mode == "auto":
        ref_type = os.getenv("GITHUB_REF_TYPE", "")
        ref_name = os.getenv("GITHUB_REF_NAME", "")
        if ref_type == "tag" and ref_name.startswith("v") and SEMVER_RE.match(ref_name[1:]):
            mode = "release"
            args.release_version = ref_name[1:]
        else:
            mode = "unreleased"

    if mode == "release":
        release_version = validate_semver(args.release_version or version, "release version")
        if release_version != version:
            raise ValueError(
                f"Release tag version ({release_version}) and VERSION file ({version}) must match"
            )

    old_content = changelog_path.read_text(encoding="utf-8") if changelog_path.exists() else ""
    preamble, sections = parse_changelog_sections(old_content)
    if not preamble.strip():
        preamble = KEEP_A_CHANGELOG_PREAMBLE + "\n"

    existing_unreleased_body = ""
    preserved_versions: list[tuple[str, str | None, str]] = []
    for name, date, body in sections:
        if name == "Unreleased":
            existing_unreleased_body = body
            continue
        preserved_versions.append((name, date, body))

    generated_unreleased_grouped = build_unreleased_from_git(repo_root)
    existing_unreleased_grouped = parse_grouped_section(existing_unreleased_body)
    unreleased_grouped = merge_grouped_sections(existing_unreleased_grouped, generated_unreleased_grouped)
    unreleased_text = render_section("Unreleased", None, unreleased_grouped, empty_note="_No unreleased changes yet._")

    final_sections: list[str] = [unreleased_text]

    if mode == "release":
        existing_release = next((section for section in preserved_versions if section[0] == version), None)
        if existing_release is None:
            current_tag = f"v{version}"
            release_grouped = build_release_from_git(repo_root, current_tag)
            today = dt.date.today().isoformat()
            release_text = render_section(version, today, release_grouped, empty_note="_No notable changes in this release._")
        else:
            existing_name, existing_date, existing_body = existing_release
            heading = f"## [{existing_name}]" + (f" - {existing_date}" if existing_date else "")
            release_text = (heading + "\n\n" + existing_body.strip() + "\n").replace("\n\n\n", "\n\n")
        final_sections.append(release_text)
        for name, date, body in preserved_versions:
            if name == version:
                continue
            heading = f"## [{name}]" + (f" - {date}" if date else "")
            section = (heading + "\n\n" + body.strip() + "\n").replace("\n\n\n", "\n\n")
            final_sections.append(section)
    else:
        for name, date, body in preserved_versions:
            heading = f"## [{name}]" + (f" - {date}" if date else "")
            section = (heading + "\n\n" + body.strip() + "\n").replace("\n\n\n", "\n\n")
            final_sections.append(section)

    new_content = preamble.rstrip() + "\n\n" + "\n".join(s.strip() + "\n" for s in final_sections)
    new_content = re.sub(r"\n{3,}", "\n\n", new_content).rstrip() + "\n"

    changelog_path.write_text(new_content, encoding="utf-8")
    print("Updated CHANGELOG.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
