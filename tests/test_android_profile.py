"""The android profile's own checks: the readme_image's picture is checked in a checkout (the repository holds the images),
but not in the app's tool copy on a device (the assets carry no docs/), where the check would fail every profile that names one."""
import copy
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / 'nxnative/dm/android/tools'))
import android_profile  # noqa: E402  (the profile reader, dm/android/tools/android_profile.py)

GAME = '010040801A4C8000'   # Blasphemous 2: its android.tsv names a readme_image


class ReadmeImageTest(unittest.TestCase):
    def setUp(self):
        self.repo = android_profile.REPO
        self.addCleanup(setattr, android_profile, 'REPO', self.repo)
        profile = android_profile.load(GAME)
        self.assertIsNotNone(profile, f'{GAME} has an android.tsv')
        self.assertTrue(profile.get('readme_image'), f'{GAME} names a readme_image')

    def broken(self):
        """the profile with a picture that is in no repository"""
        profile = copy.copy(android_profile.load(GAME))
        profile.rows = [(k, 'docs/images/no-such-picture.jpg' if k == 'readme_image' else v) for k, v in profile.rows]
        return profile

    def test_a_missing_picture_is_refused_in_a_checkout(self):
        with self.assertRaises(android_profile.ProfileError):
            self.broken().check()

    def test_the_picture_is_not_looked_for_without_the_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            android_profile.REPO = Path(directory)   # the app's tool copy on a device: no docs/ there
            android_profile.load(GAME).check()       # the profile's own picture passes
            self.broken().check()                    # and a picture that is nowhere passes too


if __name__ == '__main__':
    unittest.main()
