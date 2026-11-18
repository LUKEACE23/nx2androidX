# The player's own game profiles: the profiles they imported (a package of nxnative/dm/games/<title ID>/ folders) into their own folder.
#
# The layer lives in the player's nx2android folder, not in the tool tree the app unpacks, so an app update never touches it: the player imports
# the package once and keeps it. It is not part of DM_GAMES_DIR (that variable keeps upstream's meaning: the provisional and derived profiles of a
# port); the tools that read a profile find the layer's own ones through it (porter.profiles).
#
# The tools that look in dm/games find nothing there on the phone (the app carries no profiles): the rewriter (patch_nso.py finds a profile's
# patches.py only there) and the host's profile header. A port copies the imported profile there (profiles_layer.copy_to_games) and removes the
# copy when it is done.
#
# The package's layout is checked before anything is written: an entry that is not a profile folder, or that tries to leave the layer, is
# refused, and every profile folder has to hold a game.tsv. The layer's receipt records what came in.
import hashlib, json, os, re, shutil, time, zipfile
from pathlib import Path

from porter.paths import DM, user_dir
from porter.revision import PROFILES_COMMIT, PROFILES_REVISION

RECEIPT = 'manifest.json'          # what the import wrote: the package's revision and commit, and the profiles it holds
MARKER = '.nx2a-imported'          # the file in a profile folder that copy_to_games copied: the tree's own profiles have none
ID = re.compile(r'[0-9A-F]{16}')    # a profile folder's name: the base game's title ID


class LayerError(Exception):
    """The package cannot be imported"""


def layer_dir():
    """The player's profile layer: the folder the imported profiles live in"""
    return user_dir() / 'profiles'


def layer_profile(tid):
    """The player's imported profile of a game (its folder), None when the layer holds none"""
    tid = f'{int(tid):016X}' if isinstance(tid, int) else str(tid).upper()
    d = layer_dir() / tid
    return d if (d / 'game.tsv').is_file() else None


def layer_profiles():
    """Every profile folder the player imported"""
    d = layer_dir()
    if not d.is_dir(): return []
    return sorted(p for p in d.iterdir() if p.is_dir() and ID.fullmatch(p.name) and (p / 'game.tsv').is_file())


def in_checkout(path):
    """The path is inside a git checkout (the repository's own tree: not ours to write)"""
    for d in [Path(path), *Path(path).parents]:
        if (d / '.git').exists(): return True
    return False


def copy_to_games(tid, games=None):
    """The player's imported profile into the tool tree's dm/games, where the tools that look there find it: the rewriter (patch_nso.py finds a
    profile's patches.py only there, so without the copy a profile's cuts are not applied) and the host's profile header. A profile the tree has
    of its own (the desktop bundle, a checkout) is left alone, and a checkout is never written to. The caller removes the copy when it is done
    with it. → the copy's folder, or None when there was nothing to copy"""
    src = layer_profile(tid)
    if not src: return None
    games = Path(games or DM / 'games')
    dst = games / tid
    if (dst / 'game.tsv').is_file() and not (dst / MARKER).is_file(): return None   # the tree's own profile: not ours to replace
    if in_checkout(games): return None   # a checkout: its profiles are the ones to use, and they are not ours to write
    shutil.rmtree(dst, ignore_errors=True)
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns('__pycache__'))
    (dst / MARKER).write_text(f'copied from {src}\n', encoding='utf-8')
    return dst


def receipt():
    """What the last import wrote (None when the layer has none)"""
    path = layer_dir() / RECEIPT
    if not path.is_file(): return None
    try: return json.loads(path.read_text(encoding='utf-8'))
    except Exception: return None


def imported():
    """The title IDs the layer holds"""
    d = layer_dir()
    if not d.is_dir(): return []
    return sorted(p.name for p in d.iterdir() if p.is_dir() and ID.fullmatch(p.name))


def _entries(zf, source):
    """The package's profile files, as (zip info, parts) pairs; the parts are the path inside the layer.

    A package holds the profile folders at its root, or under dm/games/ (the repository's own layout). Anything else, and any entry that
    tries to leave the layer, is refused.
    """
    out = []
    for info in zf.infolist():
        if info.is_dir(): continue
        name = info.filename.replace('\\', '/')
        parts = [p for p in name.split('/') if p and p != '.']
        if parts[:2] == ['dm', 'games']: parts = parts[2:]
        if parts == [RECEIPT]: continue   # the package's own receipt, read below
        if not parts or '..' in parts or name.startswith('/') or not ID.fullmatch(parts[0]):
            raise LayerError(f'{source}: {info.filename!r} is not a game profile folder')
        out.append((info, parts))
    return out


def import_zip(path):
    """Imports a profiles package into the layer. Returns the receipt it wrote.

    The package's files are checked before any of them is written: every profile folder has to hold a game.tsv, so a package that is not a
    profiles package changes nothing. The receipt records the package's digest, so the app can tell whether the player imported the package
    this build made (the app carries the same digest in its own manifest).
    """
    source = Path(path).expanduser()
    if not source.is_file(): raise LayerError(f'{source} is not a file')
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    with zipfile.ZipFile(source) as zf:
        package = {}
        if RECEIPT in zf.namelist():
            try: package = json.loads(zf.read(RECEIPT).decode('utf-8'))
            except Exception: package = {}
        entries = _entries(zf, source.name)
        ids = sorted({parts[0] for _, parts in entries})
        if not ids: raise LayerError(f'{source.name}: the package holds no game profile')
        for i in ids:
            if not any(parts[0] == i and parts[-1] == 'game.tsv' for _, parts in entries):
                raise LayerError(f'{source.name}: the profile {i} has no game.tsv')
        layer = layer_dir(); layer.mkdir(parents=True, exist_ok=True)
        for info, parts in entries:
            dst = layer.joinpath(*parts)
            dst.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(info) as f, open(dst, 'wb') as out: shutil.copyfileobj(f, out)
    rec = {'revision': package.get('revision', PROFILES_REVISION), 'commit': package.get('commit', PROFILES_COMMIT),
           'date': package.get('date', ''), 'imported_at': time.strftime('%Y-%m-%d'), 'count': len(ids), 'ids': ids,
           'package': source.name, 'sha256': digest}
    (layer / RECEIPT).write_text(json.dumps(rec, indent=2) + '\n', encoding='utf-8')
    return rec
