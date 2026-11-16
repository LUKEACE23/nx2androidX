"""The QQ group's announcement (devtools/qq_release_announce.py): the Chinese changelog is taken out of the
release's own notes, the announcement stays short, and the links have a way out."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'devtools'))
import qq_release_announce as ann   # noqa: E402


NOTES = """The Android line's fourteenth alpha.

## Changelog

### New games

- **13 Sentinels: Aegis Rim (Asia)** - the Chinese release.

### New

- **The port shows what it is doing.** The rewriter's own progress lines reach the screen.

### Fixed

- **An archive too big for the phone's memory is mapped instead of refused.** 13 Sentinels' 4.2 GB archive.

### Community ports

- **@zfitness** - the Asian release.

## Before you start

- **Your own files only.**

---

## 更新日志

Android 线的第十四个 alpha。

### 新游戏

- **《13 Sentinels: Aegis Rim》亚洲版** —— 中文版（日文、韩文版同理）。

### 新增

- **移植过程会说自己在干什么。** 改写器自己的进度行会显示在屏幕上，正在跑的移植不会再被当成卡死。

### 修复

- **比手机内存还大的归档改成映射，不再拒绝。** 《13 Sentinels》的 4.2 GB 资源归档。

### Community ports

- **@zfitness** —— 亚洲版的移植。

## 开始之前

- **只用自己的文件。**
"""

CHANGELOG = """# Changelog

## v0.3.0-alpha14 - 2026-11-16

### New games

- 13 Sentinels: Aegis Rim (Asia) - the Chinese release.

### New

- The port shows what it is doing.

### Fixed

- An archive too big for the phone's memory is mapped instead of refused.

## v0.3.0-alpha13 - 2026-11-15

### New

- an older version's line, which must not be picked up
"""


class ChineseBullets(unittest.TestCase):
    """the Chinese part of the release's notes, one line per section"""

    def test_the_sections_come_out_in_the_order_a_player_reads_them(self):
        bullets = ann.chinese_bullets(NOTES)
        self.assertEqual([b[0] for b in bullets], ['新游戏', '新', '修复'])

    def test_the_markdown_is_out_of_the_line(self):
        line = dict(ann.chinese_bullets(NOTES))['新游戏']
        self.assertNotIn('*', line)
        self.assertTrue(line.startswith('《13 Sentinels'), line)

    def test_a_line_is_cut_to_its_limit(self):
        long = '### 新增\n\n- ' + ('很长的一句话' * 30) + '\n'
        line = ann.chinese_bullets('## 更新日志\n\n' + long)[0][1]
        self.assertLessEqual(len(line), ann.MAX_BULLET_CHARS)
        self.assertTrue(line.endswith('…'))

    def test_notes_without_a_chinese_part_give_nothing(self):
        self.assertEqual(ann.chinese_bullets('## Changelog\n\n### New\n\n- English only\n'), [])
        self.assertEqual(ann.chinese_bullets(None), [])

    def test_the_english_changelog_is_the_fallback(self):
        bullets = ann.english_bullets(CHANGELOG, 'v0.3.0-alpha14')
        self.assertEqual([b[0] for b in bullets], ['new game', 'new', 'fixed'])
        self.assertTrue(dict(bullets)['fixed'].startswith('An archive too big'))

    def test_the_english_changelog_does_not_reach_into_the_older_version(self):
        bullets = ann.english_bullets(CHANGELOG, 'v0.3.0-alpha14')
        self.assertNotIn('an older version', ' '.join(t for _, t in bullets))


class Content(unittest.TestCase):
    """the announcement itself"""

    def test_it_carries_the_version_the_changelog_and_the_links(self):
        content = ann.build_content('v0.3.0-alpha14', [('新', '进度上屏')])
        self.assertIn('nx2androidX v0.3.0-alpha14', content)
        self.assertIn('• 新: 进度上屏', content)
        self.assertIn('https://github.com/zFitness/nx2androidX/releases/tag/v0.3.0-alpha14', content)
        self.assertIn(ann.QUARK_LINK, content)

    def test_without_the_links_it_says_where_to_look(self):
        content = ann.build_content('v0.3.0-alpha14', [('新', '进度上屏')], links=False)
        self.assertNotIn(ann.QUARK_LINK, content)
        self.assertNotIn('github.com', content)
        self.assertIn('群公告', content)

    def test_it_is_short(self):
        bullets = [(f'要点{i}', '很长的一句话' * 20) for i in range(10)]
        content = ann.build_content('v0.3.0-alpha14', bullets)
        self.assertLessEqual(len(content), ann.MAX_CONTENT_CHARS)
        self.assertLessEqual(len([ln for ln in content.splitlines() if ln.startswith('•')]), ann.MAX_BULLETS)


class Sending(unittest.TestCase):
    """the call itself"""

    def test_a_refused_call_says_why(self):
        calls = []

        def fake(method, url, headers=None, body=None, timeout=ann.TIMEOUT):
            calls.append((method, url, body))
            return 200, {'id': 'ROBOT1.0_x'}

        original = ann.http
        ann.http = fake
        try:
            sent, detail = ann.send_group_message('OPENID', 'TOKEN', 'hello')
        finally:
            ann.http = original
        self.assertTrue(sent)
        self.assertIn('took it', detail)
        self.assertEqual(calls[0][1], f'{ann.API}/v2/groups/OPENID/messages')
        self.assertEqual(calls[0][2], {'msg_type': 0, 'content': 'hello'})
        self.assertEqual(calls[0][0], 'POST')

    def test_the_release_is_addressed_by_its_id(self):
        """the API takes no tag where a release is written: only its id"""
        calls = []

        def fake(method, url, headers=None, body=None, timeout=ann.TIMEOUT):
            calls.append((method, url, body))
            return 200, {}

        original = ann.http
        ann.http = fake
        try:
            self.assertTrue(ann.mark_announced('zFitness/nx2androidX', 42, 'the notes', 'TOKEN'))
        finally:
            ann.http = original
        self.assertEqual(calls[0][0], 'PATCH')
        self.assertEqual(calls[0][1], f'{ann.GITHUB_API}/repos/zFitness/nx2androidX/releases/42')
        self.assertIn(ann.MARKER, calls[0][2]['body'])

    def test_a_release_that_carries_the_marker_is_left_alone(self):
        self.assertFalse(ann.mark_announced('zFitness/nx2androidX', 42, 'the notes\n\n' + ann.MARKER, 'TOKEN'))

    def test_a_refused_url_is_recognized_by_its_code(self):
        """the platform's own code says that a URL is not allowed: the announcement goes without it"""
        def fake(method, url, headers=None, body=None, timeout=ann.TIMEOUT):
            return 400, {'code': 40054010, 'message': '不允许发送URL'}

        original = ann.http
        ann.http = fake
        try:
            sent, detail = ann.send_group_message('OPENID', 'TOKEN', 'hello')
        finally:
            ann.http = original
        self.assertFalse(sent)
        self.assertIn('40054010', detail)


if __name__ == '__main__':
    unittest.main()
