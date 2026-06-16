from __future__ import annotations

from pathlib import Path


_SRC_PACKAGE = Path(__file__).resolve().parents[1] / "src" / "notifyphone"
__path__ = [str(_SRC_PACKAGE)]
