#!/usr/bin/env python3
"""Update CHANGELOG.md by promoting [Unreleased] to a new release section.

Parses git history since the previous version tag, categorizes commits via
conventional-commit prefixes, and writes entries in the project's existing
format (RDFA-XXX prefix when present, with linked short SHA and PR number).

Usage:
    update_changelog.py --version 1.1.0 [--date 2026-05-07] [--repo SOPTIM/RDFArchitect]
                        [--from-ref vX.Y.Z] [--to-ref HEAD] [--dry-run]
"""
from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from collections import OrderedDict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CHANGELOG = REPO_ROOT / "CHANGELOG.md"

# Conventional commit type → changelog section.
TYPE_TO_SECTION = {
    "feat": "Added",
    "fix": "Fixed",
    "perf": "Changed",
    "refactor": "Changed",
    "revert": "Changed",
    "style": "Changed",
    "build": "Changed",
    "ci": "Changed",
    "chore": "Changed",
    "docs": "Changed",
    "test": "Changed",
}
SECTION_ORDER = ["Breaking Changes", "Added", "Changed", "Fixed"]

# Skip dependency bumps and similar noise from the changelog by default.
SKIP_SCOPES = {"deps", "deps-dev"}

COMMIT_RE = re.compile(
    r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]+)\))?(?P<bang>!)?:\s*(?P<subject>.+)$"
)
META_RE = re.compile(r"\(([^()]*#\d+[^()]*)\)\s*$")
PR_RE = re.compile(r"#(\d+)")
JIRA_RE = re.compile(r"\b([A-Z][A-Z0-9]+-\d+)\b")


def run(*args: str) -> str:
    return subprocess.check_output(args, cwd=REPO_ROOT, text=True).strip()


def previous_tag() -> str | None:
    try:
        return run("git", "describe", "--tags", "--abbrev=0",
                   "--match", "v[0-9]*.[0-9]*.[0-9]*")
    except subprocess.CalledProcessError:
        return None


def detect_repo_slug() -> str:
    url = run("git", "config", "--get", "remote.origin.url")
    m = re.search(r"github\.com[:/]([^/]+/[^/.]+)", url)
    if not m:
        raise SystemExit(f"Cannot derive repo slug from remote URL: {url}")
    return m.group(1)


def collect_commits(from_ref: str | None, to_ref: str) -> list[tuple[str, str]]:
    rev_range = f"{from_ref}..{to_ref}" if from_ref else to_ref
    out = run("git", "log", rev_range, "--no-merges",
              "--pretty=format:%H%x09%s")
    commits = []
    for line in out.splitlines():
        if not line.strip():
            continue
        sha, _, subject = line.partition("\t")
        commits.append((sha, subject))
    return commits


def classify(subject: str) -> tuple[str, str, str | None] | None:
    m = COMMIT_RE.match(subject)
    if not m:
        return None
    ctype = m.group("type").lower()
    scope = (m.group("scope") or "").lower()
    bang = m.group("bang")
    body = m.group("subject").strip()

    if scope in SKIP_SCOPES:
        return None

    section = "Breaking Changes" if bang else TYPE_TO_SECTION.get(ctype)
    if section is None:
        return None
    return section, body, scope or None


def format_entry(sha: str, subject_body: str, repo: str) -> str:
    meta_match = META_RE.search(subject_body)
    refs_text = ""
    pr_number: str | None = None
    jira: str | None = None
    description = subject_body

    if meta_match:
        refs_text = meta_match.group(1)
        description = subject_body[: meta_match.start()].rstrip()
        pr_m = PR_RE.search(refs_text)
        if pr_m:
            pr_number = pr_m.group(1)
        jira_m = JIRA_RE.search(refs_text)
        if jira_m:
            jira = jira_m.group(1)

    # Also scan the description itself for a JIRA id (e.g. "RDFA-123: foo").
    if not jira:
        jira_m = JIRA_RE.search(description)
        if jira_m:
            jira = jira_m.group(1)
            description = JIRA_RE.sub("", description, count=1)
            description = re.sub(r"^\s*[:\-]\s*", "", description).strip()

    description = description[:1].upper() + description[1:] if description else description

    short_sha = sha[:8]
    parts = [f"[{short_sha}](https://github.com/{repo}/commit/{short_sha})"]
    if pr_number:
        parts.append(f"[#{pr_number}](https://github.com/{repo}/pull/{pr_number})")
    refs = ", ".join(parts)

    prefix = f"{jira}: " if jira else ""
    return f"- {prefix}{description} ({refs})"


def build_section(version: str, date: str, sections: "OrderedDict[str, list[str]]") -> str:
    lines = [f"## [{version}] - {date}", ""]
    for name in SECTION_ORDER:
        entries = sections.get(name)
        if not entries:
            continue
        lines.append(f"### {name}")
        lines.append("")
        lines.extend(entries)
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def update_file(new_block: str, version: str) -> None:
    text = CHANGELOG.read_text(encoding="utf-8")
    if f"## [{version}]" in text:
        raise SystemExit(f"CHANGELOG.md already contains section for {version}")
    if "## [Unreleased]" not in text:
        raise SystemExit("CHANGELOG.md is missing the [Unreleased] section")
    replacement = "## [Unreleased]\n\n" + new_block
    new_text = text.replace("## [Unreleased]", replacement, 1)
    # Collapse any accidental triple newline at the boundary.
    new_text = re.sub(r"\n{3,}", "\n\n", new_text)
    if not new_text.endswith("\n"):
        new_text += "\n"
    CHANGELOG.write_text(new_text, encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--version", required=True, help="New version, e.g. 1.1.0")
    p.add_argument("--date", default=dt.date.today().isoformat())
    p.add_argument("--repo", default=None, help="owner/name; auto-detected from origin")
    p.add_argument("--from-ref", default=None, help="Defaults to last v* tag")
    p.add_argument("--to-ref", default="HEAD")
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()

    if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
        raise SystemExit(f"--version must be semver X.Y.Z, got {args.version!r}")

    repo = args.repo or detect_repo_slug()
    from_ref = args.from_ref or previous_tag()

    sections: "OrderedDict[str, list[str]]" = OrderedDict()
    for sha, subject in collect_commits(from_ref, args.to_ref):
        classified = classify(subject)
        if classified is None:
            continue
        section, body, _scope = classified
        sections.setdefault(section, []).append(format_entry(sha, body, repo))

    if not any(sections.values()):
        raise SystemExit("No changelog-worthy commits found in range")

    block = build_section(args.version, args.date, sections)

    if args.dry_run:
        sys.stdout.write(block)
        return 0

    update_file(block, args.version)
    sys.stdout.write(block)
    return 0


if __name__ == "__main__":
    sys.exit(main())
