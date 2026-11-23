#!/usr/bin/env python3
"""把一局的攻关档案导成一个交给上游的 zip。

    python3 $NX2A_REPO/tools/gamespec_pack.py --game <title ID>            # 打那局的档案
    python3 $NX2A_REPO/tools/gamespec_pack.py --game <title ID> --out <zip>

包里是那局的攻关文档（spec.md）、改过的文件（files/，按它们在工具树里的相对路径）与清单（fix.md）。
导出的是快照：档案本身不动，导出完了还能继续往里记。

一条红线守着这个包：游戏文件与密钥不进包。清单里写了游戏文件、密钥或者工具树之外的路径，
这个包就不打——并说清是哪一项被拒。
"""
import argparse
import os
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from nx2a_common import add_json, emit, fail, home, size_text  # noqa: E402

SPEC = 'spec.md'
MANIFEST = 'fix.md'
FILES = 'files'

# 游戏文件与密钥：这些名字的东西不进包（仓库的红线：游戏文件与 prod.keys 不进包、不进聊天）。
FORBIDDEN_NAMES = ('prod.keys', '.keys')
FORBIDDEN_SUFFIXES = ('.nsp', '.nsz', '.xci', '.xcz', '.nca', '.nso')


def gamespec_dir(game):
    """那局的档案文件夹：玩家 nx2android 文件夹下的 gamespec/<title ID>/。"""
    return home() / 'gamespec' / game


def refuse_reason(path):
    """这一项为什么不能进包；None 表示它没问题。"""
    if not path or path.strip() == '':
        return '清单里有一行是空的'
    if path.startswith('/') or (len(path) > 1 and path[1] == ':'):
        return f'{path}：不是相对路径'
    parts = Path(path).parts
    if '..' in parts:
        return f'{path}：路径里往上走了'
    name = parts[-1] if parts else path
    lower = name.lower()
    for bad in FORBIDDEN_NAMES:
        if bad in lower:
            return f'{path}：密钥不进包'
    for bad in FORBIDDEN_SUFFIXES:
        if lower.endswith(bad):
            return f'{path}：游戏文件不进包'
    return None


def read_manifest(manifest):
    """清单里的那些相对路径（空行与注释不算）。"""
    if not manifest.is_file():
        fail(f'这局没有 {MANIFEST}：没有改过文件就没有清单')
    out = []
    for line in manifest.read_text(encoding='utf-8').splitlines():
        text = line.strip()
        if not text or text.startswith('#'):
            continue
        out.append(text)
    return out


def collect(case, paths):
    """包里要收的文件（相对路径 -> 档案里的那份），清单与 files/ 对不上的当场拒。"""
    files = {}
    for path in paths:
        bad = refuse_reason(path)
        if bad:
            fail(bad)
        source = case / FILES / path
        if not source.is_file():
            fail(f'清单里列了 {path}，但 files/ 里没有它')
        files[path] = source
    return files


def pack(case, out, paths, files):
    """打成 zip：攻关文档、清单、改过的文件（保持它们在工具树里的相对路径）。"""
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zf:
        for name in (SPEC, MANIFEST):
            source = case / name
            if source.is_file():
                zf.write(source, name)
        for path, source in sorted(files.items()):
            zf.write(source, f'{FILES}/{path}')
    return out


def main():
    parser = argparse.ArgumentParser(description="把一局的攻关档案导成交上游的 zip")
    parser.add_argument('--game', required=True, help='那局的 title ID')
    parser.add_argument('--out', default='', help='zip 放在哪儿（默认：档案旁边）')
    add_json(parser)
    args = parser.parse_args()

    case = gamespec_dir(args.game)
    if not case.is_dir():
        fail(f'这局没有攻关档案：{case}')
    spec = case / SPEC
    if not spec.is_file():
        fail(f'这局的档案里没有 {SPEC}：攻关还没写文档')

    paths = read_manifest(case / MANIFEST)
    files = collect(case, paths)
    out = Path(args.out) if args.out else case.parent / f'{args.game}-gamespec.zip'
    pack(case, out, paths, files)

    payload = {
        'game': args.game,
        'zip': str(out),
        'bytes': out.stat().st_size,
        'files': sorted(files),
    }
    text = '\n'.join([
        f'packed {out} ({size_text(payload["bytes"])})',
        f'  {SPEC}: the case file',
        f'  {MANIFEST}: {len(paths)} file(s) listed',
    ] + [f'  {path}' for path in payload['files']])
    emit(args, payload, text)


if __name__ == '__main__':
    main()
