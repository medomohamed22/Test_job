"""Health guard: notice when Hermes' memory, SOUL or skills suddenly shrink or vanish."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from .scanner import classify, scan


@dataclass
class BrainState:
    memory: dict[str, int] = field(default_factory=dict)   # rel path -> bytes (memory + SOUL)
    skills: dict[str, int] = field(default_factory=dict)   # "profile/skill" -> total bytes

    @property
    def memory_bytes(self) -> int:
        return sum(self.memory.values())


def measure(root: Path | str) -> BrainState:
    st = BrainState()
    for e in scan(root):
        profile, kind, item = classify(e.rel)
        if kind in ("memory", "soul"):
            st.memory[e.rel] = e.size
        elif kind == "skill":
            key = f"{profile}/{item}"
            st.skills[key] = st.skills.get(key, 0) + e.size
    return st


@dataclass
class Alarm:
    what: str        # "memory" | "skills"
    message: str
    restore_hint: str


# A shrink is alarming only when it is both relative and absolute: trimming one fact is normal,
# losing most of the memory in one tool call is not.
SHRINK_RATIO = 0.5
SHRINK_MIN_BYTES = 200


def compare(before: BrainState, after: BrainState) -> list[Alarm]:
    alarms: list[Alarm] = []
    for rel, size in before.memory.items():
        now = after.memory.get(rel, 0)
        lost = size - now
        if size >= SHRINK_MIN_BYTES and lost >= SHRINK_MIN_BYTES and now <= size * (1 - SHRINK_RATIO):
            pct = round(100 * lost / size)
            gone = "was deleted" if rel not in after.memory else f"shrank {pct}% ({size} → {now} bytes)"
            alarms.append(Alarm("memory", f"{rel} {gone} in a single change", "memory" if "SOUL" not in rel else "soul"))
    removed = sorted(set(before.skills) - set(after.skills))
    if len(removed) >= 2 or (removed and len(before.skills) <= 3):
        names = ", ".join(r.split("/", 1)[1] for r in removed[:6])
        alarms.append(Alarm("skills", f"{len(removed)} skill(s) removed at once: {names}", "skills"))
    return alarms


def format_alarms(alarms: list[Alarm], snapshot_id: Optional[str]) -> str:
    lines = ["⚠ Hermes Ark health guard:"]
    for a in alarms:
        lines.append(f"  - {a.message}")
    if snapshot_id:
        hints = sorted({a.restore_hint for a in alarms})
        lines.append(f"  The previous version is saved in snapshot {snapshot_id}. "
                     f"To undo, the user can run: " + " ; ".join(f"/ark restore {h} {snapshot_id} yes" for h in hints))
    lines.append("  Tell the user about this change and ask whether it was intended.")
    return "\n".join(lines)
