#!/usr/bin/env python3
"""Synchronize CHANGELOG.md from git commits.

Modes:
- main: append missing entries to [Unreleased] from a commit range
- release: move [Unreleased] into a tagged release section and recreate [Unreleased]
- auto: infer mode from GitHub env vars
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

REPO_URL = "https://github.com/SOPTIM/RDFArchitect"
CATEGORY_ORDER = (
    "Breaking Changes",
    "Added",
    "Changed",
    "Deprecated",
    "Removed",
    "Fixed",
)

SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$")
SECTION_HEADER_RE = re.compile(r"^## \[([^\]]+)\](?: - (\d{4}-\d{2}-\d{2}))?\s*$")
COMMIT_HEADER_RE = re.compile(
    r"^(?P<type>[A-Za-z]+)(?:\((?P<scope>[^)]+)\))?(?P<breaking>!)?:\s*(?P<description>.+)$"
)
PR_RE = re.compile(r"(?:\(#(\d+)\)|(?<![A-Za-z0-9])#(\d+)\b)")
RDFA_RE = re.compile(r"\b(RDFA-\d+)\b", re.IGNORECASE)
GH_ISSUE_RE = re.compile(r"\bGH-(\d+)\b", re.IGNORECASE)
REMOVE_RE = re.compile(r"\b(remove|removed|delete|deleted)\b", re.IGNORECASE)
DEPRECATE_RE = re.compile(r"\b(deprecate|deprecated|deprecates|deprecation)\b", re.IGNORECASE)
ADD_RE = re.compile(r"\b(add|added|adding)\b", re.IGNORECASE)
BREAKING_BODY_RE = re.compile(r"^\s*BREAKING CHANGE:\s+", re.IGNORECASE | re.MULTILINE)
COMMIT_LINK_RE = re.compile(r"/commit/([0-9a-fA-F]{7,40})")
PULL_LINK_RE = re.compile(r"/pull/(\d+)")
ISSUE_LINK_RE = re.compile(r"/issues/(\d+)")
BULLET_RE = re.compile(r"^\s*-\s+(.*)$")
PLACEHOLDER_BULLET_RE = re.compile(r"^-\s*_No .+_$")

DEFAULT_PREAMBLE = """# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)."""


@dataclass
class Section:
    name: str
    date: str | None
    body: str


@dataclass
class CommitRecord:
    sha: str
    subject: str
    body: str


@dataclass
class ParsedCommit:
    sha: str
    sha8: str
    description: str
    category: str
    is_breaking: bool
    pr_id: str | None
    rdfa_id: str | None
    gh_issue_id: str | None
    entry: str
    non_compliant: bool


class DedupIndex:
    def __init__(self) -> None:
        self.shas: set[str] = set()
        self.pr_ids: set[str] = set()
        self.gh_issue_ids: set[str] = set()
        self.rdfa_ids: set[str] = set()
        self.descriptions: set[str] = set()

    def add_existing_bullet(self, bullet_text: str) -> None:
        for sha in COMMIT_LINK_RE.findall(bullet_text):
            self.shas.add(sha.lower())
            self.shas.add(sha[:8].lower())

        for pull_id in PULL_LINK_RE.findall(bullet_text):
            self.pr_ids.add(pull_id)

        for issue_id in ISSUE_LINK_RE.findall(bullet_text):
            self.gh_issue_ids.add(issue_id)

        for match in PR_RE.finditer(bullet_text):
            pr_id = next((g for g in match.groups() if g), None)
            if pr_id:
                self.pr_ids.add(pr_id)

        for issue_id in GH_ISSUE_RE.findall(bullet_text):
            self.gh_issue_ids.add(issue_id)

        rdfa = RDFA_RE.search(bullet_text)
        if rdfa:
            self.rdfa_ids.add(rdfa.group(1).upper())

        canonical = canonical_existing_description(bullet_text)
        if canonical:
            self.descriptions.add(canonical)

    def add_new_commit(self, parsed: ParsedCommit) -> None:
        self.shas.add(parsed.sha.lower())
        self.shas.add(parsed.sha8.lower())
        if parsed.pr_id:
            self.pr_ids.add(parsed.pr_id)
        if parsed.gh_issue_id:
            self.gh_issue_ids.add(parsed.gh_issue_id)
        if parsed.rdfa_id:
            self.rdfa_ids.add(parsed.rdfa_id)
        canonical = canonical_generated_description(parsed.description)
        if canonical:
            self.descriptions.add(canonical)

    def exists(self, parsed: ParsedCommit) -> bool:
        if parsed.sha.lower() in self.shas or parsed.sha8.lower() in self.shas:
            return True
        if parsed.pr_id and parsed.pr_id in self.pr_ids:
            return True
        if parsed.gh_issue_id and parsed.gh_issue_id in self.gh_issue_ids:
            return True
        if parsed.rdfa_id and parsed.rdfa_id in self.rdfa_ids:
            return True
        canonical = canonical_generated_description(parsed.description)
        if canonical and canonical in self.descriptions:
            return True
        return False


def run_git(args: list[str], cwd: Path) -> str:
    proc = subprocess.run(["git", *args], cwd=cwd, text=True, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def validate_semver(value: str, name: str) -> str:
    text = value.strip()
    if not SEMVER_RE.match(text):
        raise ValueError(f"{name} must be valid SemVer, got: {value}")
    return text


def is_all_zero_sha(sha: str | None) -> bool:
    return bool(sha) and set(sha) == {"0"}


def parse_sections(content: str) -> tuple[str, list[Section]]:
    lines = content.splitlines()
    headers: list[tuple[int, str, str | None]] = []

    for idx, line in enumerate(lines):
        match = SECTION_HEADER_RE.match(line)
        if match:
            headers.append((idx, match.group(1), match.group(2)))

    if not headers:
        preamble = content.strip() or DEFAULT_PREAMBLE
        return preamble + "\n", []

    preamble = "\n".join(lines[: headers[0][0]]).strip() or DEFAULT_PREAMBLE
    sections: list[Section] = []
    for i, (start, name, date) in enumerate(headers):
        end = headers[i + 1][0] if i + 1 < len(headers) else len(lines)
        body = "\n".join(lines[start + 1 : end]).strip("\n")
        sections.append(Section(name=name, date=date, body=body))

    return preamble + "\n", sections


def render_sections(preamble: str, sections: list[Section]) -> str:
    out: list[str] = [preamble.strip(), ""]

    for section in sections:
        header = f"## [{section.name}]"
        if section.date:
            header += f" - {section.date}"
        out.append(header)
        out.append("")
        if section.body.strip():
            out.append(section.body.strip("\n"))
            out.append("")

    text = "\n".join(out).rstrip() + "\n"
    return re.sub(r"\n{3,}", "\n\n", text)


def find_section(sections: list[Section], name: str) -> tuple[int, Section] | None:
    for idx, section in enumerate(sections):
        if section.name == name:
            return idx, section
    return None


def fresh_unreleased_body() -> str:
    lines: list[str] = []
    for category in CATEGORY_ORDER:
        lines.append(f"### {category}")
        lines.append("")
    while lines and lines[-1] == "":
        lines.pop()
    return "\n".join(lines)


def extract_commits(repo_root: Path, from_sha: str, to_sha: str) -> list[CommitRecord]:
    if is_all_zero_sha(from_sha):
        revspec = to_sha
    else:
        revspec = f"{from_sha}..{to_sha}"

    raw = run_git(
        ["log", "--reverse", "--no-merges", "--format=%H%x1f%s%x1f%b%x1e", revspec],
        repo_root,
    )

    commits: list[CommitRecord] = []
    for block in raw.split("\x1e"):
        entry = block.strip()
        if not entry:
            continue
        parts = entry.split("\x1f", 2)
        if len(parts) < 2:
            continue
        sha = parts[0].strip()
        subject = parts[1].strip()
        body = parts[2].strip() if len(parts) > 2 else ""
        commits.append(CommitRecord(sha=sha, subject=subject, body=body))

    return commits


def pick_first_group(regex: re.Pattern[str], text: str) -> str | None:
    for match in regex.finditer(text):
        value = next((g for g in match.groups() if g), None)
        if value:
            return value
    return None


def normalize_description(raw_description: str) -> str:
    description = raw_description.strip()

    while True:
        trailing = re.search(r"\(([^()]*)\)\s*$", description)
        if not trailing:
            break
        inside = trailing.group(1)
        if re.search(r"#\d+|RDFA-\d+|GH-\d+", inside, flags=re.IGNORECASE):
            description = description[: trailing.start()].rstrip(" -_,;:")
            continue
        break

    description = RDFA_RE.sub("", description)
    description = GH_ISSUE_RE.sub("", description)
    description = re.sub(r"(?<![A-Za-z0-9])#\d+\b", "", description)
    description = re.sub(r"\(\s*\)", "", description)
    description = re.sub(r"\[\s*\]", "", description)
    description = re.sub(r"\s+", " ", description)
    description = re.sub(r"\s+([,;:.!?])", r"\1", description)
    description = description.strip(" -_,;:\t")

    if not description:
        description = "No description provided"

    return description[0].upper() + description[1:]


def classify_category(commit_type: str | None, description: str, body: str) -> str:
    haystack = f"{description} {body}".lower()

    if DEPRECATE_RE.search(haystack):
        return "Deprecated"
    if REMOVE_RE.search(haystack):
        return "Removed"

    if commit_type == "fix":
        return "Fixed"
    if commit_type == "feat":
        if ADD_RE.search(description):
            return "Added"
        return "Changed"

    return "Changed"


def format_entry(
    sha: str,
    description: str,
    pr_id: str | None,
    rdfa_id: str | None,
    gh_issue_id: str | None,
) -> str:
    sha8 = sha[:8]
    prefix = ""
    if gh_issue_id:
        prefix = f"[GH-{gh_issue_id}]({REPO_URL}/issues/{gh_issue_id}): "

    meta_parts: list[str] = []
    if pr_id:
        meta_parts.append(f"[#{pr_id}]({REPO_URL}/pull/{pr_id})")
    if rdfa_id:
        meta_parts.append(rdfa_id)
    meta_parts.append(f"[{sha8}]({REPO_URL}/commit/{sha})")

    return f"{prefix}{description} ({', '.join(meta_parts)})"


def parse_commit(record: CommitRecord) -> ParsedCommit:
    non_compliant = False
    commit_type: str | None = None
    header_breaking = False
    description_source = record.subject

    header_match = COMMIT_HEADER_RE.match(record.subject)
    if header_match:
        commit_type = header_match.group("type").lower()
        header_breaking = bool(header_match.group("breaking"))
        description_source = header_match.group("description")
    else:
        non_compliant = True

    combined = f"{record.subject}\n{record.body}".strip()
    pr_id = pick_first_group(PR_RE, combined)
    rdfa_match = RDFA_RE.search(combined)
    rdfa_id = rdfa_match.group(1).upper() if rdfa_match else None
    gh_match = GH_ISSUE_RE.search(combined)
    gh_issue_id = gh_match.group(1) if gh_match else None

    description = normalize_description(description_source)
    category = classify_category(commit_type, description, record.body)
    is_breaking = header_breaking or bool(BREAKING_BODY_RE.search(record.body))

    entry = format_entry(
        sha=record.sha,
        description=description,
        pr_id=pr_id,
        rdfa_id=rdfa_id,
        gh_issue_id=gh_issue_id,
    )

    return ParsedCommit(
        sha=record.sha,
        sha8=record.sha[:8],
        description=description,
        category=category,
        is_breaking=is_breaking,
        pr_id=pr_id,
        rdfa_id=rdfa_id,
        gh_issue_id=gh_issue_id,
        entry=entry,
        non_compliant=non_compliant,
    )


def canonical_generated_description(description: str) -> str:
    text = re.sub(r"\s+", " ", description).strip().lower()
    return text


def canonical_existing_description(bullet_text: str) -> str:
    text = bullet_text.strip()
    text = re.sub(r"^\[GH-\d+\]\([^)]+\):\s*", "", text, flags=re.IGNORECASE)
    text = re.sub(r"^GH-\d+:\s*", "", text, flags=re.IGNORECASE)

    while True:
        trailing = re.search(r"\(([^()]*)\)\s*$", text)
        if not trailing:
            break
        inside = trailing.group(1)
        if re.search(r"#\d+|RDFA-\d+|GH-\d+|/pull/|/issues/|/commit/|[0-9a-f]{7,40}", inside, flags=re.IGNORECASE):
            text = text[: trailing.start()].rstrip()
            continue
        break

    return canonical_generated_description(text)


def build_dedup_index(changelog_content: str) -> DedupIndex:
    index = DedupIndex()
    for line in changelog_content.splitlines():
        bullet_match = BULLET_RE.match(line)
        if not bullet_match:
            continue
        index.add_existing_bullet(bullet_match.group(1).strip())
    return index


def append_entries_to_unreleased(body: str, entries_by_category: dict[str, list[str]]) -> str:
    lines = body.splitlines() if body.strip() else []

    for category in CATEGORY_ORDER:
        entries = entries_by_category.get(category, [])
        if not entries:
            continue

        heading = f"### {category}"
        heading_index = next((i for i, line in enumerate(lines) if line.strip() == heading), None)

        if heading_index is None:
            if lines and lines[-1].strip():
                lines.append("")
            lines.append(heading)
            lines.append("")
            lines.extend(f"- {entry}" for entry in entries)
            lines.append("")
            continue

        next_heading_index = len(lines)
        for idx in range(heading_index + 1, len(lines)):
            if lines[idx].strip().startswith("### "):
                next_heading_index = idx
                break

        block = lines[heading_index + 1 : next_heading_index]
        block = [line for line in block if not PLACEHOLDER_BULLET_RE.match(line.strip())]

        lines = lines[: heading_index + 1] + block + lines[next_heading_index:]

        insertion_point = heading_index + 1 + len(block)
        to_insert: list[str] = []
        if insertion_point == 0 or (insertion_point > 0 and lines[insertion_point - 1].strip()):
            to_insert.append("")
        to_insert.extend(f"- {entry}" for entry in entries)
        to_insert.append("")

        lines[insertion_point:insertion_point] = to_insert

    while lines and not lines[-1].strip():
        lines.pop()

    return "\n".join(lines)


def handle_main_mode(
    repo_root: Path,
    sections: list[Section],
    changelog_content: str,
    from_sha: str,
    to_sha: str,
) -> tuple[list[Section], list[str], int]:
    warnings: list[str] = []

    section_result = find_section(sections, "Unreleased")
    if section_result is None:
        sections.insert(0, Section(name="Unreleased", date=None, body=fresh_unreleased_body()))
        unreleased = sections[0]
    else:
        unreleased = section_result[1]

    commits = extract_commits(repo_root, from_sha, to_sha)
    dedupe = build_dedup_index(changelog_content)

    entries_by_category: dict[str, list[str]] = {category: [] for category in CATEGORY_ORDER}
    added_count = 0

    for commit in commits:
        parsed = parse_commit(commit)

        if parsed.non_compliant:
            warnings.append(
                f"Commit {parsed.sha8} does not follow the target format; parsed using best effort: {commit.subject}"
            )

        if dedupe.exists(parsed):
            continue

        entries_by_category[parsed.category].append(parsed.entry)
        added_count += 1
        if parsed.is_breaking:
            entries_by_category["Breaking Changes"].append(parsed.entry)
            added_count += 1

        dedupe.add_new_commit(parsed)

    unreleased.body = append_entries_to_unreleased(unreleased.body, entries_by_category)
    return sections, warnings, added_count


def handle_release_mode(
    sections: list[Section],
    release_version: str,
    release_date: str,
) -> list[Section]:
    unreleased_idx = find_section(sections, "Unreleased")
    unreleased_body = ""
    if unreleased_idx is not None:
        idx, section = unreleased_idx
        unreleased_body = section.body.strip()
        sections.pop(idx)

    if not unreleased_body:
        unreleased_body = "### Changed\n\n- _No notable changes in this release._"

    release_idx = find_section(sections, release_version)
    if release_idx is None:
        release_section = Section(name=release_version, date=release_date, body=unreleased_body)
        sections.insert(0, release_section)
    else:
        idx, section = release_idx
        existing = section.body.strip()
        if unreleased_body and unreleased_body not in existing:
            section.body = (unreleased_body + "\n\n" + existing).strip()
        if not section.date:
            section.date = release_date
        sections[idx] = section

    sections.insert(0, Section(name="Unreleased", date=None, body=fresh_unreleased_body()))
    return sections


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["auto", "main", "release"], default="auto")
    parser.add_argument("--from-sha", default="")
    parser.add_argument("--to-sha", default="")
    parser.add_argument("--release-version", default="")
    parser.add_argument("--release-date", default="")
    return parser


def main() -> int:
    args = build_parser().parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    changelog_path = repo_root / "CHANGELOG.md"
    version_path = repo_root / "VERSION"

    changelog_content = changelog_path.read_text(encoding="utf-8") if changelog_path.exists() else DEFAULT_PREAMBLE + "\n"
    preamble, sections = parse_sections(changelog_content)

    version = validate_semver(version_path.read_text(encoding="utf-8").strip(), "VERSION")

    mode = args.mode
    from_sha = args.from_sha
    to_sha = args.to_sha
    release_version = args.release_version
    release_date = args.release_date

    if mode == "auto":
        ref_type = os.getenv("GITHUB_REF_TYPE", "")
        ref_name = os.getenv("GITHUB_REF_NAME", "")
        if ref_type == "tag" and ref_name.startswith("v") and SEMVER_RE.match(ref_name[1:]):
            mode = "release"
            release_version = ref_name[1:]
            release_date = dt.datetime.now(dt.UTC).date().isoformat()
        else:
            mode = "main"
            from_sha = os.getenv("GITHUB_EVENT_BEFORE", "")
            to_sha = os.getenv("GITHUB_SHA", "")

    warnings: list[str] = []
    added_count = 0

    if mode == "main":
        if not from_sha or not to_sha:
            raise ValueError("main mode requires --from-sha and --to-sha")
        sections, warnings, added_count = handle_main_mode(repo_root, sections, changelog_content, from_sha, to_sha)
        if added_count == 0:
            print("No changelog changes")
            for warning in warnings:
                print(f"WARNING: {warning}", file=sys.stderr)
            return 0

    elif mode == "release":
        if not release_version:
            raise ValueError("release mode requires --release-version")
        validate_semver(release_version, "release version")
        if release_version != version:
            raise ValueError(
                f"Release version ({release_version}) does not match VERSION file ({version})"
            )
        if not release_date:
            release_date = dt.datetime.now(dt.UTC).date().isoformat()
        sections = handle_release_mode(sections, release_version, release_date)

    new_content = render_sections(preamble, sections)
    if new_content != changelog_content:
        changelog_path.write_text(new_content, encoding="utf-8")
        print("Updated CHANGELOG.md")
    else:
        print("No changelog changes")

    for warning in warnings:
        print(f"WARNING: {warning}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
