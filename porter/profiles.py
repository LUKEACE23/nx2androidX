# The games the tool knows: every game profile in nxnative/dm/games, the ones the player imported (porter.profiles_layer), and whether each has been ported
# to Android (its android.tsv, dm/android/tools/android_profile.py).
import re
from dataclasses import dataclass, asdict

from porter import profiles_layer
from porter.upstream import android_profile, game


@dataclass
class Entry:
    id: str
    name: str
    android: bool        # has an android.tsv
    status: str
    tested: str
    package: str

    def as_dict(self): return asdict(self)


def entry(g, p):
    if p: return Entry(g.id, g.get('name', g.id), True, p.status, p.tested, p.package)
    return Entry(g.id, g.get('name', g.id), False, 'not ported', '', f'org.nx2a.t{g.id.lower()}')


def _android(g):
    return android_profile.AndroidProfile(g) if (g.dir / 'android.tsv').is_file() else None


def all_profiles():
    """(game profile, Android profile or None) of every game the tool knows: the repository's own profiles first, then the ones the player imported"""
    out, seen = [], set()
    for g in list(game.all_games()) + [game.Game(d) for d in profiles_layer.layer_profiles()]:
        if g.id in seen: continue
        seen.add(g.id); out.append((g, _android(g)))
    return out


def all_entries(android_only=True):
    rows = [entry(g, p) for g, p in all_profiles()]
    return [r for r in rows if r.android or not android_only]


def by_title(tid):
    """(game profile, Android profile or None) of a base game's title ID; (None, None) when no profile matches.
    The player's imported profiles are not in DM_GAMES_DIR, so the layer is asked for the ones the tree does not have"""
    if isinstance(tid, str) and re.fullmatch(r'[0-9A-Fa-f]{16}', tid): tid = int(tid, 16)
    g = game.by_title(tid)
    if not g:
        d = profiles_layer.layer_profile(tid)
        g = game.Game(d) if d else None
    if not g: return None, None
    return g, _android(g)


def load(gid):
    """The Android profile of a game profile id (ProfileError when it has none)"""
    g, p = by_title(gid)
    if not g: raise android_profile.ProfileError(f'{gid}: no game profile')
    if not p: raise android_profile.ProfileError(f'{gid} has no android.tsv: it is not ported to Android')
    return p
