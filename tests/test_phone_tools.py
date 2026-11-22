"""The phone's tools: what each one says about a game, a log, a build, a card and a frame.

The tools are scripts in phone_tools/ that the assistant runs on the phone; here they are run the
same way (as a program, with its arguments), against folders built for the test.
"""
import json
import os
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'phone_tools'
sys.path.insert(0, str(TOOLS))


def run(tool, *args, env=None):
    """A tool, run as the assistant runs it."""
    environment = dict(os.environ)
    environment.update(env or {})
    return subprocess.run([sys.executable, str(TOOLS / tool), *[str(a) for a in args]],
                          capture_output=True, text=True, env=environment)


class MemLayout(unittest.TestCase):
    PROFILE = """# a profile
name\tTest Game

# ---- Memory ----
mem_reserve\t0x100000000
mem_alias\t0x10000000
mem_stack\t0x10000000
app_pool\t0xCD500000
"""

    def profile(self, text=None, folder=None):
        folder = folder or tempfile.mkdtemp(prefix='nx2a_profile_')
        path = Path(folder) / 'game.tsv'
        path.write_text(text if text is not None else self.PROFILE)
        return path

    def test_the_layout_is_computed(self):
        path = self.profile()
        result = run('mem_layout.py', '--profile', path, '--json')
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        self.assertEqual(out['reserve'], 0x100000000)
        self.assertEqual(out['alias'], 0x10000000)
        self.assertEqual(out['stack'], 0x10000000)
        # the heap is what is left of the block after the node area, the alias window and the stack
        self.assertEqual(out['heap'], 0x100000000 - (0x10000000 + 0x10000000 + 0x10000000))

    def test_both_notations_are_shown(self):
        result = run('mem_layout.py', '--profile', self.profile())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('0x10000000', result.stdout)
        self.assertIn('268,435,456', result.stdout)

    def test_a_layout_that_does_not_fit_is_an_error(self):
        text = self.PROFILE.replace('mem_alias\t0x10000000', 'mem_alias\t0xF0000000')
        result = run('mem_layout.py', '--profile', self.profile(text))
        self.assertEqual(result.returncode, 2)
        self.assertIn('does not fit', result.stderr)
        self.assertNotIn('Traceback', result.stderr)

    def test_a_profile_without_the_rows_is_an_error(self):
        result = run('mem_layout.py', '--profile', self.profile('name\tX\n'))
        self.assertEqual(result.returncode, 2)
        self.assertIn('mem_reserve', result.stderr)


class LogTriage(unittest.TestCase):
    def test_the_crash_is_found_in_the_previous_log(self):
        folder = Path(tempfile.mkdtemp(prefix='nx2a_log_'))
        (folder / 'log.txt').write_text('[dm] guest AS 0x7f0000\n[perf] frame 12, 16.6 ms\n')
        (folder / 'log.prev.txt').write_text('[dm] CRASH sig=11\n  backtrace:\n    main+0x1234\n')
        result = run('log_triage.py', '--port', folder, '--json')
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        self.assertIn('log.prev.txt', out['verdict'])
        crashes = out['logs'][1]['crashes']
        self.assertTrue(crashes)
        self.assertEqual(crashes[0]['kind'], 'crash')

    def test_a_run_without_a_crash_says_so(self):
        folder = Path(tempfile.mkdtemp(prefix='nx2a_log_'))
        (folder / 'log.txt').write_text('[dm] guest AS 0x7f0000\n')
        result = run('log_triage.py', '--port', folder)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('no crash signature', result.stdout)

    def test_a_missing_pack_is_an_error(self):
        result = run('log_triage.py', '--port', '/nonexistent/pack')
        self.assertEqual(result.returncode, 2)
        self.assertIn('no data pack', result.stderr)


class Builds(unittest.TestCase):
    def test_the_builds_are_listed(self):
        home = Path(tempfile.mkdtemp(prefix='nx2a_home_'))
        folder = home / 'builds' / 'abc'
        folder.mkdir(parents=True)
        (folder / 'record.json').write_text(json.dumps(
            {'id': 'abc', 'game': 'Game.nsp', 'phase': 'FAILED', 'stage': 'shader split',
             'seconds': 42, 'startedAt': 1732300000000}))
        (folder / 'log.txt').write_text('working\n[RED] the shader table does not match\n')
        result = run('builds.py', '--json', env={'NX2A_HOME': str(home)})
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        self.assertEqual(len(out['builds']), 1)
        self.assertEqual(out['builds'][0]['game'], 'Game.nsp')

    def test_the_first_error_is_shown(self):
        home = Path(tempfile.mkdtemp(prefix='nx2a_home_'))
        folder = home / 'builds' / 'abc'
        folder.mkdir(parents=True)
        (folder / 'record.json').write_text(json.dumps(
            {'id': 'abc', 'game': 'Game.nsp', 'phase': 'FAILED', 'stage': 'shader split', 'seconds': 42}))
        (folder / 'log.txt').write_text('working\n[RED] the shader table does not match\n')
        result = run('builds.py', '--errors', env={'NX2A_HOME': str(home)})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('the shader table does not match', result.stdout)


class PortStatus(unittest.TestCase):
    def snapshot(self, games):
        home = Path(tempfile.mkdtemp(prefix='nx2a_home_'))
        (home / 'library.json').write_text(json.dumps(
            {'schema': 'nx2a.library.v1', 'generatedAt': 1, 'count': len(games), 'games': games}))
        return home

    def test_every_game_is_listed(self):
        games = [{'name': f'Game {i}', 'titleId': f'{i:016X}', 'status': 'ported', 'pack': ''}
                 for i in range(12)]
        home = self.snapshot(games)
        result = run('port_status.py', '--json', env={'NX2A_HOME': str(home)})
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        self.assertEqual(out['count'], 12)   # not the first ten

    def test_a_crashed_pack_is_marked(self):
        pack = Path(tempfile.mkdtemp(prefix='nx2a_pack_'))
        (pack / 'log.txt').write_text('[dm] CRASH sig=11\n')
        home = self.snapshot([{'name': 'Game', 'titleId': '0100000000000000', 'status': 'ported',
                               'pack': str(pack)}])
        result = run('port_status.py', '--json', env={'NX2A_HOME': str(home)})
        out = json.loads(result.stdout)
        self.assertEqual(out['games'][0]['run'], 'crashed')

    def test_a_missing_snapshot_says_where_it_should_be(self):
        home = Path(tempfile.mkdtemp(prefix='nx2a_home_'))
        result = run('port_status.py', env={'NX2A_HOME': str(home)})
        self.assertEqual(result.returncode, 2)
        self.assertIn('library.json', result.stderr)


class Shot(unittest.TestCase):
    def frame(self, width=4, height=2, color=(0, 0, 0)):
        pixels = bytes(color) * (width * height)
        header = f'P6\n{width} {height}\n255\n'.encode()
        path = Path(tempfile.mkdtemp(prefix='nx2a_frame_')) / 'frame_000001.ppm'
        path.write_bytes(header + pixels)
        return path

    def test_the_statistics_are_computed(self):
        path = self.frame(color=(10, 20, 30))
        result = run('shot.py', 'stats', path, '--json')
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        self.assertEqual(out['frames'][0]['mean'], [10.0, 20.0, 30.0])
        self.assertEqual(out['frames'][0]['black_share'], 0.0)

    def test_a_black_frame_is_black(self):
        path = self.frame()
        result = run('shot.py', 'stats', path, '--json')
        out = json.loads(result.stdout)
        self.assertEqual(out['frames'][0]['black_share'], 1.0)

    def test_a_png_is_written(self):
        path = self.frame()
        result = run('shot.py', 'png', path)
        self.assertEqual(result.returncode, 0, result.stderr)
        png = path.with_suffix('.png')
        self.assertTrue(png.is_file())
        self.assertTrue(png.read_bytes().startswith(b'\x89PNG'))

    def test_two_frames_are_compared(self):
        folder = Path(tempfile.mkdtemp(prefix='nx2a_frames_'))
        first = folder / 'frame_1.ppm'
        second = folder / 'frame_2.ppm'
        first.write_bytes(b'P6\n2 1\n255\n' + bytes((0, 0, 0)) * 2)
        second.write_bytes(b'P6\n2 1\n255\n' + bytes((255, 255, 255)) * 2)
        result = run('shot.py', 'diff', first, second, '--json')
        out = json.loads(result.stdout)
        self.assertEqual(out['changed_share'], 1.0)
        self.assertEqual(out['mean_abs_difference'], 255.0)

    def test_a_file_that_is_not_a_frame_is_an_error(self):
        path = Path(tempfile.mkdtemp(prefix='nx2a_frame_')) / 'x.ppm'
        path.write_bytes(b'not a ppm')
        result = run('shot.py', 'stats', path)
        self.assertEqual(result.returncode, 2)
        self.assertIn('not a PPM', result.stderr)


class Card(unittest.TestCase):
    """A card dump with tickets: the titles are named by the tickets' file names."""

    def dump(self, name, titles):
        sys.path.insert(0, str(ROOT))
        from porter import dumps
        files = [(f'{t:016x}{i:016x}.tik', bytes(16)) for i, t in enumerate(titles)]
        data = (dumps._pfs0_header([n for n, _ in files], [len(d) for _, d in files])
                + b''.join(d for _, d in files))
        path = Path(tempfile.mkdtemp(prefix='nx2a_card_')) / name
        path.write_bytes(data)
        return path

    def test_the_titles_are_listed(self):
        base = 0x0100000000AAA000
        path = self.dump('Merged.nsp', [base, base + 0x800, base + 0x1001])
        result = run('card.py', path, '--json')
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        self.assertEqual(len(out['titles']), 3)
        self.assertIn(f'{base:016X}', out['titles'])
        self.assertIn(f'{base + 0x800:016X}', out['titles'])

    def test_a_file_that_is_not_a_dump_is_an_error(self):
        path = Path(tempfile.mkdtemp(prefix='nx2a_card_')) / 'x.nsp'
        path.write_bytes(b'not a dump')
        result = run('card.py', path)
        self.assertEqual(result.returncode, 2)
        self.assertNotIn('Traceback', result.stderr)


class UpstreamDiff(unittest.TestCase):
    def test_the_three_groups_are_told_apart(self):
        import upstream_diff
        mine = {'porter/a.py': '1', 'porter/b.py': '2', 'porter/only-mine.py': '3'}
        theirs = {'porter/a.py': '1', 'porter/b.py': '9', 'porter/only-upstream.py': '4'}
        only_mine, only_theirs, different = upstream_diff.compare(mine, theirs)
        self.assertEqual(only_mine, ['porter/only-mine.py'])
        self.assertEqual(only_theirs, ['porter/only-upstream.py'])
        self.assertEqual(different, ['porter/b.py'])


class ToolHousekeeping(unittest.TestCase):
    def test_every_tool_has_a_help_and_a_json_flag(self):
        for tool in sorted(TOOLS.glob('*.py')):
            if tool.name.startswith('nx2a_common'): continue
            with self.subTest(tool=tool.name):
                result = run(tool.name, '--help')
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn('--json', result.stdout)

    def test_the_readme_lists_every_tool(self):
        readme = (TOOLS / 'README.md').read_text()
        for tool in sorted(TOOLS.glob('*.py')):
            if tool.name.startswith('nx2a_common'): continue
            self.assertIn(tool.name, readme, f'{tool.name} is not in the README')


if __name__ == '__main__':
    unittest.main()
