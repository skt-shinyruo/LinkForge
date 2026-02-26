from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class PackageOccurrence:
    module: str
    file: Path


def iter_java_files(src_root: Path) -> list[Path]:
    if not src_root.is_dir():
        return []
    return sorted(p for p in src_root.rglob("*.java") if p.name != "module-info.java")


def parse_package(java_file: Path) -> str | None:
    try:
        # Read only the head; package declaration must appear at the top in this repo.
        head = java_file.read_text(encoding="utf-8").splitlines()[:80]
    except Exception:
        return None

    for line in head:
        s = line.strip()
        if not s or s.startswith("//") or s.startswith("/*") or s.startswith("*"):
            continue
        if s.startswith("package ") and s.endswith(";"):
            return s.removeprefix("package ").removesuffix(";").strip()
        # If we reached imports or class definition without a package, stop early.
        if s.startswith("import ") or s.startswith("public ") or s.startswith("class ") or s.startswith("@"):
            break
    return None


def find_split_packages(repo_root: Path, modules: list[str]) -> dict[str, list[PackageOccurrence]]:
    occurrences: dict[str, list[PackageOccurrence]] = {}
    for module in modules:
        src_root = repo_root / "server" / module / "src" / "main" / "java"
        for java_file in iter_java_files(src_root):
            pkg = parse_package(java_file)
            if not pkg:
                continue
            occurrences.setdefault(pkg, []).append(PackageOccurrence(module=module, file=java_file))

    split: dict[str, list[PackageOccurrence]] = {}
    for pkg, occ in occurrences.items():
        owners = {o.module for o in occ}
        if len(owners) > 1:
            split[pkg] = occ
    return split


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Detect Java split packages across backend Maven modules.",
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Repository root path (default: current directory).",
    )
    parser.add_argument(
        "--modules",
        default="platform,api,edge",
        help="Comma-separated server modules to scan (default: platform,api,edge).",
    )
    args = parser.parse_args(argv)

    repo_root = Path(args.repo_root).resolve()
    modules = [m.strip() for m in str(args.modules).split(",") if m.strip()]
    if not modules:
        print("No modules specified.", file=sys.stderr)
        return 2

    split = find_split_packages(repo_root, modules)
    if not split:
        print("OK: no split packages detected.")
        return 0

    print("ERROR: split packages detected (same Java package appears in multiple modules):", file=sys.stderr)
    for pkg in sorted(split.keys()):
        occ = split[pkg]
        owners = sorted({o.module for o in occ})
        examples = {}
        for o in occ:
            examples.setdefault(o.module, o.file)
        example_str = ", ".join(f"{m}: {examples[m].relative_to(repo_root)}" for m in owners)
        print(f"- {pkg} -> {', '.join(owners)} ({example_str})", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
