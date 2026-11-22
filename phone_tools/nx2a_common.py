"""What the phone's tools have in common: where they read, what they print, how they fail.

Every tool in this folder is a script the assistant runs on the phone:

    python3 $NX2A_REPO/tools/<name>.py --help

They read the player's nx2android folder ($NX2A_HOME) and the porter's tree ($NX2A_REPO), and
print a table a human reads. With --json they print the same thing for a program.

Two rules the tools follow:

- a failure is one line that says what went wrong, never a Python traceback;
- nothing is written outside what the player asked for: the tools read, except the ones that
  write a file the player named (the split, the fetched page, the frame).
"""
import json
import os
import sys
from pathlib import Path


def home():
    """The player's nx2android folder: the app's own folder on the phone (files/nx2a)."""
    env = os.environ.get('NX2A_HOME')
    return Path(env) if env else Path.cwd()


def repo():
    """The porter's tool tree: the app's files/tool on the phone."""
    env = os.environ.get('NX2A_REPO')
    return Path(env) if env else Path.cwd()


def fail(message, code=2):
    """One line about what went wrong, and no traceback."""
    sys.stderr.write(f'error: {message}\n')
    raise SystemExit(code)


def table(rows, headers=None, empty='(nothing)'):
    """A table a human reads: the columns lined up, the empty case said out loud."""
    rows = [[('' if c is None else str(c)) for c in row] for row in rows]
    if not rows:
        return empty
    head = list(headers) if headers else []
    width = [len(h) for h in head]
    for row in rows:
        for i, cell in enumerate(row):
            if i >= len(width): width.append(0)
            width[i] = max(width[i], len(cell))
    lines = []
    if head:
        lines.append('  '.join(h.ljust(width[i]) for i, h in enumerate(head)).rstrip())
        lines.append('  '.join('-' * width[i] for i in range(len(width))))
    for row in rows:
        lines.append('  '.join(cell.ljust(width[i]) for i, cell in enumerate(row)).rstrip())
    return '\n'.join(lines)


def emit(args, payload, text):
    """What the tool prints: the JSON for a program, the text for a human."""
    if getattr(args, 'json', False):
        print(json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True))
    else:
        print(text)


def add_json(parser):
    """The flag every tool has: the same result for a program to read."""
    parser.add_argument('--json', action='store_true', help='print the result as JSON instead of a table')
    return parser


def both_hex(value):
    """A number in both notations: 0x8000000 and 128 MB."""
    return f'0x{value:X} ({value:,})'


def size_text(value):
    """A byte count a human reads: 1.2 MB."""
    if value < 1024: return f'{value} B'
    if value < 1024 ** 2: return f'{value / 1024:.1f} KB'
    if value < 1024 ** 3: return f'{value / 1024 ** 2:.1f} MB'
    return f'{value / 1024 ** 3:.2f} GB'
