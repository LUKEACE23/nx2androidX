---
name: card-unpack
description: 拆卡、解包、把卡带分成本体/升级档/DLC 时用：把 XCI 或 NSP 卡带拆成每个 title 一个 NSP，放到玩家在 App 里能选到的位置。
---

# 拆卡：卡带 → 每个 title 一个 NSP

玩家说「拆卡」「解包」「把卡带分成本体和升级档」，或者要拿一个单独的本体/升级档去移植、去测试时，走这个流程。

## 用工具，不要自己写脚本

- `python3 $NX2A_REPO/tools/card.py "<卡带>" --keys $NX2A_HOME/../prod.keys`：先看卡里有什么（有哪些 title、升级档是不是合并在里面）。只读。
- `python3 $NX2A_REPO/tools/card_split.py "<卡带>" "<输出目录>"`：拆成每个 title 一个 NSP。prod.keys 默认读 $NX2A_HOME/../prod.keys。

拆包不要自己写脚本：以前写在临时目录里的那套不在工具树里，换台手机就没了，卡带格式一变就废。

要看游戏文件本体（romfs、exefs 的内容）是另一件事，用 nx_extract；给 App 选文件不需要它。

## 输出的位置和名字（铁律）

- 输出目录是 `/storage/emulated/0/Download/<游戏名>-<本体 title ID>/`，例如
  `/storage/emulated/0/Download/火焰纹章风花雪月-010055D009F78000/`。
  - App 的文件选择器看不到 App 私有目录（files/…、files/tmp、files/tmpwork、$TMPDIR），玩家选不到那里的文件。
  - 私有目录只配当中间产物（scratch），用完删掉。
- 目录里平铺，不要再套子目录：不要建「本体/升级档/DLC」子目录，玩家明确不要，一层放平。
- 文件名由工具定：`<title ID>_base.nsp`、`<title ID>_update.nsp`、`<title ID>_dlc.nsp`（DLC 的编号在 title ID 里），别的叫 `_other.nsp`。不要自己改名。
- 不要动玩家自己的卡带：不 move、不改名、不删它的原件。拆包读它，写到别的目录去。

## 空间

- 拆出来的 NSP 加起来差不多等于卡带的大小（13 GB 的卡带要 13 GB 的地方）。写之前先看：`df -h /storage/emulated/0 | tail -1`。
- 工具自己也会算：不够就一行报错，不会写一半。
- 不够就跟玩家说清楚，别自己删玩家的东西腾地方。

## 做完

- 告诉玩家每个 NSP 的名字、大小和它们在哪个目录。
- 中间产物（解出来的 romfs、清单、临时目录）删掉，除非玩家要留。