#!/usr/bin/env python3
"""Fail CI when a local Markdown link points to a missing repository file."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[2]
LINK_PATTERN = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
MARKDOWN_FILES = [ROOT / "README.md", ROOT / "SECURITY.md", *sorted((ROOT / "docs").rglob("*.md"))]


def normalize_destination(raw: str) -> str:
    destination = raw.strip()
    if destination.startswith("<") and destination.endswith(">"):
        destination = destination[1:-1]
    elif " " in destination:
        destination = destination.split(" ", 1)[0]
    return unquote(destination)


def main() -> int:
    failures: list[str] = []
    for document in MARKDOWN_FILES:
        if not document.is_file():
            continue
        content = document.read_text(encoding="utf-8")
        for match in LINK_PATTERN.finditer(content):
            destination = normalize_destination(match.group(1))
            parsed = urlsplit(destination)
            if not destination or destination.startswith("#") or parsed.scheme or parsed.netloc:
                continue
            target = (document.parent / parsed.path).resolve()
            try:
                target.relative_to(ROOT)
            except ValueError:
                failures.append(f"{document.relative_to(ROOT)}: link escapes repository: {destination}")
                continue
            if not target.exists():
                line = content.count("\n", 0, match.start()) + 1
                failures.append(
                    f"{document.relative_to(ROOT)}:{line}: missing local target: {destination}"
                )

    if failures:
        print("Local Markdown link validation failed:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(f"Validated local links in {len(MARKDOWN_FILES)} Markdown files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
