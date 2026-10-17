#!/usr/bin/env python3
"""Deterministic random-word generator for the spoofed manager build.

The spoofed manager renames its package, classes and native libraries to
random lowercase words. Generating those words from /dev/urandom makes
every CI run produce a different package name, so builds cannot be
installed over each other and the name changes for no reason.

Instead, words are derived from a *seed* that changes only when the
manager version code crosses a 100 boundary (versionCode // 100). Pushes
within the same hundred (40100..40199) keep the exact same words, while
crossing into the next hundred (40200) rotates them. That way the
package identity is stable for users across dozens of builds while still
rotating regularly for detection purposes.

Usage: random_words.py <seed>
Prints 15 lowercase words, one per line. Deterministic: the same seed
always yields the same sequence.
"""

import random
import string
import sys


def generate(seed: int) -> list[str]:
    rng = random.Random(seed)
    return ["".join(rng.choices(string.ascii_lowercase, k=6)) for _ in range(15)]


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <seed>", file=sys.stderr)
        return 2
    try:
        seed = int(sys.argv[1])
    except ValueError:
        print(f"seed must be an integer, got: {sys.argv[1]}", file=sys.stderr)
        return 2
    for word in generate(seed):
        print(word)
    return 0


if __name__ == "__main__":
    sys.exit(main())
