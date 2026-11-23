#!/usr/bin/env python3
"""Search the distilled upstream cases: the offline half of tools/upstream_pr.py.

    python3 kb_search.py "black screen"
    python3 kb_search.py --symptom black --layer vk
    python3 kb_search.py --source droidplay --symptom crash
    python3 kb_search.py --game "Pokémon" --json
    python3 kb_search.py --local "黑屏"          # the player's own cases

upstream_pr.py asks GitHub whether a fix exists there. This one asks what the corpus already
knows, and it answers when the phone's network cannot reach GitHub at all (which is most of
the time in China). It reads the case index that ships with the knowledge base, so it needs
no network and no tool tree.

The index holds the cases of this fork's upstream, `shirai91/nx2android`. Every row has a
`source` column, so the search can say which upstream a case came from, and a second
upstream's cases would land in the same index and be found by the same search.

The words are matched against the case's title, its summary, its game and its
symptom. With --symptom / --layer / --area / --kind the search is by field instead, which is
the sharper way to ask "what did the Vulkan backend break, and is the fix in this fork".

--local searches the other corpus: the player's own case files (gamespec/<title ID>/spec.md).
Those are the cases this phone already worked through - the same symptom on another game is
often already solved there, and the answer is one grep away. They are the player's data, so
they survive every app update.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

# The columns a search may look at.
TEXT_FIELDS = ('title', 'summary', 'game', 'symptom', 'layer', 'area', 'ref')

# The fields of a local case that a search may look at.
LOCAL_FIELDS = ('game', 'title', 'status', 'symptom', 'cause', 'fix')


def index_path(explicit=''):
    """Where the case index is: the path given, or the one beside this script."""
    if explicit:
        return Path(explicit)
    here = Path(__file__).resolve().parent
    for candidate in (here.parent / 'references/case-index.tsv',
                      here.parent.parent / 'references/case-index.tsv'):
        if candidate.is_file():
            return candidate
    return here.parent / 'references/case-index.tsv'


def read_index(path):
    """The case index as a list of dicts (the header names the columns)."""
    if not path.is_file():
        return []
    lines = path.read_text(encoding='utf-8').splitlines()
    if not lines:
        return []
    header = lines[0].split('\t')
    rows = []
    for line in lines[1:]:
        if not line.strip():
            continue
        cells = line.split('\t')
        cells += [''] * (len(header) - len(cells))
        rows.append(dict(zip(header, cells)))
    return rows


def matches(row, words):
    """Does the row hold every word? The words are matched as plain text, case aside."""
    blob = '\n'.join(row.get(field, '') for field in TEXT_FIELDS).lower()
    return all(word.lower() in blob for word in words)


def fields_match(row, filters):
    """The field filters: an exact value (a field may hold several, comma separated)."""
    for field, wanted in filters.items():
        if not wanted:
            continue
        held = [part.strip() for part in row.get(field, '').split(',')]
        if wanted not in held:
            return False
    return True


def text_of(rows, words):
    """What a reader sees: one line per hit, then what it means."""
    lines = []
    if words:
        lines.append('searched the distilled cases for: ' + ' '.join(words))
        lines.append('')
    for row in rows:
        source = row.get('source', '')
        reference = row.get('ref', '')
        # a case of this fork's upstream is a pull request; another upstream's would be a
        # document and an anchor in it
        label = f"#{reference}" if source == 'shirai91' else reference
        state = row.get('state', '')
        if state:
            state = ' (' + state + ')'
        lines.append(f"{source} {label}{state} [{row.get('symptom', '')}/"
                     f"{row.get('layer', '')}] in this fork: {row.get('fork', '?')}")
        lines.append(f"  {row.get('title', '')}")
        if row.get('summary'):
            lines.append(f"  {row['summary'][:220]}")
        if row.get('url'):
            lines.append(f"  {row['url']}")
    if not rows:
        lines.append('(nothing found)')
    return '\n'.join(lines)


# ---------------------------------------------------------------- the player's own cases

# A problem entry in a case file: "### 2026-11-23 the crash (status: fixed)".
ENTRY = re.compile(r'^###\s+(?P<title>.+?)\s*\((?P<status>status:[^)]*)\)\s*$')

# The fields of an entry, English and the player's own language.
FIELDS = {
    'symptom': ('Symptom:',),
    'cause': ('Cause:',),
    'fix': ('Proposed fix:', 'Fix:'),
    'evidence': ('Evidence:',),
    'player_headline': ('Player headline:',),
    'player_cause': ('Player cause:',),
    'player_fix': ('Player fix:',),
}


def cases_dir(explicit=''):
    """Where the player's case files are: the gamespec folder of their nx2android home."""
    if explicit:
        return Path(explicit)
    home = os.environ.get('NX2A_HOME', '')
    if home:
        return Path(home) / 'gamespec'
    # the script lives in <nx2a>/agent/.agents/skills/port-failures/scripts/
    here = Path(__file__).resolve()
    for depth in range(2, len(here.parents)):
        candidate = here.parents[depth] / 'gamespec'
        if candidate.is_dir():
            return candidate
    return here.parents[5] / 'gamespec'


def parse_case(spec):
    """One case file as a list of problems, plus the game's name and its timeline."""
    game = ''
    title_id = ''
    for line in spec.read_text(encoding='utf-8', errors='replace').splitlines():
        if line.startswith('# ') and not game:
            head = line[2:].strip()
            match = re.match(r'^(?P<name>.+?)\s*\((?P<id>[0-9A-Fa-f]{16})\)\s*case file', head)
            if match:
                game = match.group('name').strip()
                title_id = match.group('id')
            else:
                game = head
            break

    problems = []
    current = None
    timeline = []
    in_timeline = False
    holding = None   # the field the continuation lines belong to
    for raw in spec.read_text(encoding='utf-8', errors='replace').splitlines():
        line = raw.strip()
        if line.startswith('## '):
            in_timeline = line.lower().startswith('## timeline')
            current = None
            holding = None
            continue
        if in_timeline:
            if line.startswith('- '):
                timeline.append(line[2:].strip())
            continue
        entry = ENTRY.match(line)
        if entry:
            current = {'game': game, 'title_id': title_id,
                       'title': entry.group('title').strip(),
                       'status': entry.group('status').replace('status:', '').strip()}
            problems.append(current)
            holding = None
            continue
        if current is None:
            continue
        matched = None
        for field, prefixes in FIELDS.items():
            for prefix in prefixes:
                if line.startswith(prefix):
                    matched = field
                    value = line[len(prefix):].strip()
                    if not current.get(field):
                        current[field] = value
                    break
            if matched:
                break
        if matched:
            holding = matched
            continue
        # 续行：上一个字段的下一段（字段常常是一整段文字）
        if holding and line:
            current[holding] = (current.get(holding, '') + ' ' + line).strip()
    return problems, timeline


def read_local(root):
    """Every problem of every case file under the gamespec folder."""
    rows = []
    if not root.is_dir():
        return rows
    for spec in sorted(root.glob('*/spec.md')):
        try:
            problems, _ = parse_case(spec)
        except OSError:
            continue
        rows.extend(problems)
    return rows


def local_matches(row, words):
    """Does the case hold every word? The player's language lines count too."""
    blob = '\n'.join(row.get(field, '') or '' for field in LOCAL_FIELDS).lower()
    blob += '\n' + (row.get('player_cause', '') or '').lower()
    blob += '\n' + (row.get('player_fix', '') or '').lower()
    blob += '\n' + (row.get('player_headline', '') or '').lower()
    return all(word.lower() in blob for word in words)


def local_text_of(rows, words):
    """What a reader sees: the cases this phone already worked through."""
    lines = []
    if words:
        lines.append('searched this phone\'s own cases for: ' + ' '.join(words))
        lines.append('')
    for row in rows:
        lines.append(f"{row.get('game', '?')} ({row.get('title_id', '?')}) "
                     f"[{row.get('status', '')}]")
        lines.append(f"  {row.get('title', '')}")
        cause = row.get('player_cause') or row.get('cause')
        fix = row.get('player_fix') or row.get('fix')
        if cause:
            lines.append(f"  cause: {cause[:220]}")
        if fix:
            lines.append(f"  fix: {fix[:220]}")
        if row.get('evidence'):
            lines.append(f"  evidence: {row['evidence'][:160]}")
    if not rows:
        lines.append('(no case of this phone matches)')
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description="Search the distilled upstream cases")
    parser.add_argument('words', nargs='*', help='the words to look for')
    parser.add_argument('--symptom', default='', help='only cases with this symptom')
    parser.add_argument('--layer', default='', help='only cases in this layer')
    parser.add_argument('--area', default='', help='only cases in this area')
    parser.add_argument('--kind', default='', help='only cases of this kind (core, profile, app)')
    parser.add_argument('--game', default='', help='only cases about this game')
    parser.add_argument('--source', default='', help='only cases from this upstream')
    parser.add_argument('--fork', default='', help="only cases whose fix is in this fork (yes, no, ?)")
    parser.add_argument('--index', default='', help='the case index (found beside this script)')
    parser.add_argument('--local', action='store_true',
                        help="search this phone's own case files (gamespec/<title ID>/spec.md)")
    parser.add_argument('--cases', default='', help='where the case files are (found by themselves)')
    parser.add_argument('--json', action='store_true', help='print the result as JSON')
    args = parser.parse_args()

    # Every word of every argument counts: "black lakes" and "black" "lakes" are the same search.
    words = [word for argument in args.words for word in argument.split()]
    if args.game:
        words.extend(args.game.split())

    if args.local:
        root = cases_dir(args.cases)
        rows = read_local(root)
        if not rows:
            print(f'error: no case files under {root}', file=sys.stderr)
            return 1
        hits = [row for row in rows if local_matches(row, words)]
        if args.json:
            print(json.dumps({'ok': True, 'corpus': 'local', 'root': str(root), 'words': words,
                              'count': len(hits), 'results': hits},
                             indent=2, ensure_ascii=False, sort_keys=True))
        else:
            print(local_text_of(hits, words))
        return 0

    path = index_path(args.index)
    rows = read_index(path)
    if not rows:
        print(f'error: the case index is not at {path}', file=sys.stderr)
        return 1

    filters = {'symptom': args.symptom, 'layer': args.layer, 'area': args.area,
               'kind': args.kind, 'fork': args.fork, 'source': args.source}
    hits = [row for row in rows if fields_match(row, filters) and matches(row, words)]

    if args.json:
        print(json.dumps({'ok': True, 'words': words, 'filters': filters,
                          'count': len(hits), 'results': hits},
                         indent=2, ensure_ascii=False, sort_keys=True))
    else:
        print(text_of(hits, words))
    return 0


if __name__ == '__main__':
    sys.exit(main())
