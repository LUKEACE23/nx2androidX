"""The player's imported profile copied into the tool tree's dm/games: the rewriter finds a profile's patches.py only there (without them a profile's cuts are not
applied), and so do the host's profile header and the runtime update. The copy is refreshed at every port and left out of a checkout and of a tree that has a profile of
its own."""
import os
from pathlib import Path
import tempfile
import types
import unittest
from unittest import mock

from porter import pipeline, profiles_layer

TID = '0100B8A026F14000'


class ProfileSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        patch = mock.patch.dict(os.environ, dict(os.environ, NX2A_HOME=str(self.home)), clear=False)
        patch.start(); self.addCleanup(patch.stop)
        self.layer = profiles_layer.layer_dir() / TID
        self.layer.mkdir(parents=True)
        (self.layer / 'game.tsv').write_text(f'title_id\t{TID}\n', encoding='utf-8')
        (self.layer / 'patches.py').write_text('CUTS = {}\n', encoding='utf-8')
        (self.layer / 'android.tsv').write_text('status\tplayable\n', encoding='utf-8')
        self.dm = Path(self.temp.name) / 'dm'
        self.games = self.dm / 'games'
        self.games.mkdir(parents=True)

    def test_the_imported_profile_is_copied_into_the_games_folder(self):
        dst = pipeline.copy_imported_profile(TID, self.games)
        self.assertEqual(dst, self.games / TID)
        self.assertTrue((dst / 'patches.py').is_file())       # the rewriter's patches: the reason for the copy
        self.assertTrue((dst / 'android.tsv').is_file())
        self.assertTrue((dst / pipeline.IMPORTED).is_file())   # the marker: the copy is ours to replace

    def test_the_copy_is_refreshed_at_every_port(self):
        pipeline.copy_imported_profile(TID, self.games)
        (self.games / TID / 'patches.py').write_text("CUTS = {'aa': []}\n", encoding='utf-8')   # a copy of an older import
        dst = pipeline.copy_imported_profile(TID, self.games)
        self.assertEqual((dst / 'patches.py').read_text(encoding='utf-8'), 'CUTS = {}\n')

    def test_a_profile_the_tree_has_of_its_own_is_left_alone(self):
        own = self.games / TID
        own.mkdir()
        (own / 'game.tsv').write_text(f'title_id\t{TID}\n', encoding='utf-8')
        self.assertIsNone(pipeline.copy_imported_profile(TID, self.games))
        self.assertFalse((own / pipeline.IMPORTED).exists())
        self.assertEqual((own / 'game.tsv').read_text(encoding='utf-8'), f'title_id\t{TID}\n')

    def test_a_game_the_player_imported_no_profile_of_is_not_copied(self):
        self.assertIsNone(pipeline.copy_imported_profile('0100999999999000', self.games))
        self.assertFalse((self.games / '0100999999999000').exists())

    def test_a_checkout_is_not_written_to(self):
        (self.games / '.git').mkdir()
        self.assertIsNone(pipeline.copy_imported_profile(TID, self.games))
        self.assertFalse((self.games / TID).exists())

    def test_the_port_copies_the_profile_before_it_reads_it(self):
        port = types.SimpleNamespace(g=types.SimpleNamespace(dir=self.layer, id=TID))
        with mock.patch.object(pipeline, 'DM', self.dm):
            pipeline.Port.sync_profile(port, TID)
        self.assertTrue((self.games / TID / 'patches.py').is_file())

    def test_a_failure_to_copy_is_a_warning_not_an_error(self):
        port = types.SimpleNamespace(g=types.SimpleNamespace(dir=self.layer, id=TID))
        with mock.patch.object(pipeline, 'DM', self.dm), mock.patch.object(pipeline, 'copy_imported_profile', side_effect=OSError('read-only')):
            pipeline.Port.sync_profile(port, TID)   # no exception: the port goes on without the profile's patches


if __name__ == '__main__':
    unittest.main()
