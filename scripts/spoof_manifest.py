#!/usr/bin/env python3
"""Transform the manager AndroidManifest.xml for spoofed builds.

Removes detection-grade surfaces that survive a package rename and are
readable through public PackageManager APIs:

- intent-filters that accept zip/apk archives (VIEW / SEND / SEND_MULTIPLE
  "open with manager" profiles) or the ksu:// deep-link scheme. Any app can
  resolve these via queryIntentActivities() without root.
- app-zygote attributes (android:zygotePreloadName / isolatedProcess /
  useAppZygote). ApplicationInfo.zygotePreloadName is a rare attribute that
  fingerprints the manager even when its value is randomized.
- the "webui" taskAffinity suffix.

In-app flows are unaffected: every internal launch of these components uses
explicit intents, which bypass intent-filter matching. Only external
"open/share a zip with the manager" and external ksu:// deep links stop
working in spoofed builds.

Usage: spoof_manifest.py <AndroidManifest.xml> <task-affinity-word>
"""

import re
import sys


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(f'usage: {sys.argv[0]} <manifest> <word>')
    path, word = sys.argv[1], sys.argv[2]
    src = open(path, encoding='utf-8').read()

    dropped = []

    def drop_filter(match: re.Match) -> str:
        block = match.group(0)
        if 'application/zip' in block or 'scheme="ksu"' in block:
            dropped.append(block)
            return ''
        return block

    out = re.sub(
        r'<intent-filter[^>]*>.*?</intent-filter>\s*',
        drop_filter,
        src,
        flags=re.S,
    )

    out, n_preload = re.subn(r'\s*android:zygotePreloadName="[^"]*"', '', out)
    out, n_iso = re.subn(r'\s*android:isolatedProcess="true"', '', out)
    out, n_zygote = re.subn(r'\s*android:useAppZygote="true"', '', out)
    out, n_affinity = re.subn(
        r'android:taskAffinity="([^"]*)\.webui"',
        lambda m: f'android:taskAffinity="{m.group(1)}.{word}"',
        out,
    )

    open(path, 'w', encoding='utf-8').write(out)
    print(
        'spoof_manifest: dropped '
        f'{len(dropped)} intent-filter block(s); zygotePreloadName={n_preload} '
        f'isolatedProcess={n_iso} useAppZygote={n_zygote} '
        f'taskAffinity={n_affinity}'
    )


if __name__ == '__main__':
    main()
