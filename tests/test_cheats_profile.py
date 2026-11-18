"""金手指随数据包走：档案的 cheats 键、打包时的 build 闸门。

只依赖 android_profile 与 porter.datapack 的纯逻辑，不跑真正的编译。"""
import importlib.util
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('android_profile', REPO / 'nxnative/dm/android/tools/android_profile.py')
android_profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(android_profile)


BUILD = 'DF154E1D08E39646'          # main 的 build ID 前 16 位：金手指文件按它命名
OTHER = '0011223344556677'


class FakeGame:
    def __init__(self, directory):
        self.dir = Path(directory)
        self.id = '0100000000010000'

    def get(self, key, default=''):
        return default


class FakeProfile:
    """datapack.build() 读到的档案字段"""
    def __init__(self, directory, cheats=''):
        self.game = FakeGame(directory)
        self.id = '0100000000010000'
        self.name = 'A Game'
        self.package = 'org.nx2a.t0100000000010000'
        self.status = 'playable'
        self.graphics = 'nvn'
        self.lan = ''
        self.requires = []
        self.env = []
        self.settings = {}
        self.cheats = cheats


class CheatsProfileTest(unittest.TestCase):

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix='nx2a-cheats-'))
        self.profile_dir = self.tmp / 'profile'
        (self.profile_dir / 'cheats').mkdir(parents=True)
        self.cheat = self.profile_dir / 'cheats' / f'{BUILD}.txt'
        self.cheat.write_text('[Infinite money]\n04000000 00000000\n', encoding='utf-8')
        self.patched = self.tmp / 'patched'
        self.patched.mkdir()
        (self.patched / 'main.fixups.tsv').write_text(f'# BUILD_ID\t{BUILD}8F1C2D3E4B5A6978\n', encoding='utf-8')

    def write_profile(self, body):
        path = self.profile_dir / 'android.tsv'
        path.write_text(body, encoding='utf-8')
        return path

    # ---- 档案的 cheats 键 ----

    def test_the_key_is_read(self):
        self.write_profile(f'status\tplayable\ncheats\tcheats/{BUILD}.txt\n')
        p = android_profile.AndroidProfile(FakeGame(self.profile_dir))
        self.assertEqual(p.cheats, f'cheats/{BUILD}.txt')

    def test_a_game_without_cheats_has_none(self):
        self.write_profile('status\tplayable\n')
        p = android_profile.AndroidProfile(FakeGame(self.profile_dir))
        self.assertEqual(p.cheats, '')

    def test_a_path_outside_the_profile_folder_is_refused(self):
        self.write_profile('status\tplayable\ncheats\t../elsewhere.txt\n')
        with self.assertRaises(android_profile.ProfileError):
            android_profile.AndroidProfile(FakeGame(self.profile_dir))

    def test_a_missing_file_is_refused(self):
        self.write_profile('status\tplayable\ncheats\tcheats/DEADBEEFDEADBEEF.txt\n')
        with self.assertRaises(android_profile.ProfileError):
            android_profile.AndroidProfile(FakeGame(self.profile_dir))

    def test_the_key_given_twice_is_refused(self):
        self.write_profile(f'status\tplayable\ncheats\tcheats/{BUILD}.txt\ncheats\tcheats/{BUILD}.txt\n')
        with self.assertRaises(android_profile.ProfileError):
            android_profile.AndroidProfile(FakeGame(self.profile_dir))

    # ---- 打包 ----

    def test_the_build_id_is_the_first_sixteen_digits(self):
        from porter import datapack
        self.assertEqual(datapack.build_id(self.patched), BUILD)

    def test_no_build_id_in_the_rewriters_table(self):
        from porter import datapack
        (self.patched / 'main.fixups.tsv').write_text('# TARGET\tandroid\n', encoding='utf-8')
        self.assertEqual(datapack.build_id(self.patched), '')

    def test_the_cheat_file_of_this_build_is_picked(self):
        from porter import datapack
        p = FakeProfile(self.profile_dir, f'cheats/{BUILD}.txt')
        self.assertEqual(datapack.cheat_file(p, self.patched), self.cheat)

    def test_a_cheat_file_of_another_build_is_left_out(self):
        from porter import datapack
        other = self.profile_dir / 'cheats' / f'{OTHER}.txt'
        other.write_text('[Nope]\n', encoding='utf-8')
        p = FakeProfile(self.profile_dir, f'cheats/{OTHER}.txt')
        self.assertIsNone(datapack.cheat_file(p, self.patched))

    def test_a_file_not_named_after_a_build_is_left_out(self):
        from porter import datapack
        odd = self.profile_dir / 'cheats' / 'game.txt'
        odd.write_text('[Nope]\n', encoding='utf-8')
        p = FakeProfile(self.profile_dir, 'cheats/game.txt')
        self.assertIsNone(datapack.cheat_file(p, self.patched))

    def test_the_pack_carries_the_cheat_file_and_the_manifest_says_so(self):
        from porter import datapack
        p = FakeProfile(self.profile_dir, f'cheats/{BUILD}.txt')
        dst = self.tmp / 'pack'
        man = datapack.build(dst, profile=p, version='1.0.0', display='1.0.0', patched=self.patched, romfs=None, shaders=None)
        self.assertTrue((dst / 'cheats' / f'{BUILD}.txt').is_file())
        self.assertEqual(man['cheats'], f'cheats/{BUILD}.txt')

    def test_a_pack_of_another_build_carries_no_cheats(self):
        from porter import datapack
        p = FakeProfile(self.profile_dir, f'cheats/{BUILD}.txt')
        (self.patched / 'main.fixups.tsv').write_text(f'# BUILD_ID\t{OTHER}8F1C2D3E4B5A6978\n', encoding='utf-8')
        dst = self.tmp / 'pack2'
        man = datapack.build(dst, profile=p, version='1.0.0', display='1.0.0', patched=self.patched, romfs=None, shaders=None)
        self.assertFalse((dst / 'cheats').exists())
        self.assertNotIn('cheats', man)


if __name__ == '__main__':
    unittest.main()
