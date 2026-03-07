#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re
import sys
from collections import defaultdict


PACKAGE_RE = re.compile(r"^\s*package\s+([a-zA-Z0-9_.]+)\s*;\s*$")


def iter_java_packages(src_main_java_dir: pathlib.Path) -> set[str]:
    packages: set[str] = set()
    for path in src_main_java_dir.rglob("*.java"):
        try:
            with path.open("r", encoding="utf-8") as f:
                for line in f:
                    m = PACKAGE_RE.match(line)
                    if m:
                        packages.add(m.group(1))
                        break
        except UnicodeDecodeError:
            # Skip unexpected encodings; better to be permissive than to fail CI on tooling.
            continue
    return packages


def main() -> int:
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    server_dir = repo_root / "server"
    if not server_dir.exists():
        print("ERROR: expected server/ directory at repo root", file=sys.stderr)
        return 2

    module_src_roots: dict[str, pathlib.Path] = {}
    for pom in server_dir.rglob("pom.xml"):
        module_dir = pom.parent
        src_main_java = module_dir / "src" / "main" / "java"
        if src_main_java.exists():
            module_src_roots[str(module_dir.relative_to(repo_root))] = src_main_java

    if not module_src_roots:
        print("OK: no Maven modules with src/main/java found")
        return 0

    packages_to_modules: dict[str, set[str]] = defaultdict(set)
    for module_path, src_main_java in sorted(module_src_roots.items()):
        for pkg in iter_java_packages(src_main_java):
            packages_to_modules[pkg].add(module_path)

    split = {pkg: mods for pkg, mods in packages_to_modules.items() if len(mods) > 1}
    if not split:
        print("OK: no split packages detected")
        return 0

    print("ERROR: split packages detected (same Java package appears in multiple Maven modules):", file=sys.stderr)
    for pkg in sorted(split.keys()):
        mods = ", ".join(sorted(split[pkg]))
        print(f"  - {pkg}: {mods}", file=sys.stderr)
    print("", file=sys.stderr)
    print("Fix: move classes so that each Java package exists in only one module.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

