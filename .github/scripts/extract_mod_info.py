#!/usr/bin/env python3

import json
import sys
import zipfile

jar = sys.argv[1]

with zipfile.ZipFile(jar) as zf:
    with zf.open("fabric.mod.json") as fp:
        mod = json.load(fp)

version = mod["version"]
name = mod.get("name", "Mod")

lower = version.lower()

prerelease = any(
    x in lower
    for x in (
        "alpha",
        "beta",
        "rc",
        "snapshot",
    )
)

print(f"version={version}")
print(f"name={name}")
print(f"prerelease={'true' if prerelease else 'false'}")
