#!/usr/bin/env python3
"""Search the distilled upstream cases: the offline half of tools/upstream_pr.py.

    python3 kb_search.py "black screen"
    python3 kb_search.py --symptom black --layer vk
    python3 kb_search.py --source droidplay --symptom crash
    python3 kb_search.py --game "Pokémon" --json

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
"""
import argparse
import json
import re
import sys
from pathlib import Path

# The columns a search may look at.
TEXT_FIELDS = ('title', 'summary', 'game', 'symptom', 'layer', 'area', 'ref')


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
    parser.add_argument('--json', action='store_true', help='print the result as JSON')
    args = parser.parse_args()

    path = index_path(args.index)
    rows = read_index(path)
    if not rows:
        print(f'error: the case index is not at {path}', file=sys.stderr)
        return 1

    filters = {'symptom': args.symptom, 'layer': args.layer, 'area': args.area,
               'kind': args.kind, 'fork': args.fork, 'source': args.source}
    # Every word of every argument counts: "black lakes" and "black" "lakes" are the same search.
    words = [word for argument in args.words for word in argument.split()]
    if args.game:
        words.extend(args.game.split())
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
