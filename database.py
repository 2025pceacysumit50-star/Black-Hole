"""
Self-hosted SQLite — your own database, no cloud dependency, no third
party. This also lays groundwork for the cross-device threat network:
threat_patterns tracks how many independent devices have reported the
same suspicious URL. It only counts right now — nothing pushes alerts
to other devices yet. That's deliberate: with a handful of users, an
alerting system has nothing real to learn from. Build the push side
once there's actual multi-device traffic to justify it.
"""

import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

DB_PATH = Path(__file__).parent / "shieldcheck.db"

SCHEMA = """
CREATE TABLE IF NOT EXISTS scam_checks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    request_id TEXT UNIQUE NOT NULL,
    device_id TEXT NOT NULL,
    content_type TEXT NOT NULL,
    content TEXT NOT NULL,
    rule_score INTEGER NOT NULL,
    matched_patterns TEXT NOT NULL,
    used_llm INTEGER NOT NULL,
    verdict TEXT NOT NULL,
    confidence REAL NOT NULL,
    reasons TEXT NOT NULL,
    recommended_action TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS feedback (
    request_id TEXT PRIMARY KEY,
    was_helpful INTEGER NOT NULL,
    actual_outcome TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS threat_patterns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pattern_type TEXT NOT NULL,
    pattern_value TEXT NOT NULL,
    report_count INTEGER NOT NULL DEFAULT 1,
    first_seen TEXT NOT NULL,
    last_seen TEXT NOT NULL,
    UNIQUE(pattern_type, pattern_value)
);
"""


@contextmanager
def get_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with get_connection() as conn:
        conn.executescript(SCHEMA)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def log_check(
    request_id: str,
    device_id: str,
    content_type: str,
    content: str,
    rule_score: int,
    matched_patterns: list[str],
    used_llm: bool,
    verdict: str,
    confidence: float,
    reasons: list[str],
    recommended_action: str,
) -> None:
    with get_connection() as conn:
        conn.execute(
            """INSERT INTO scam_checks
               (request_id, device_id, content_type, content, rule_score,
                matched_patterns, used_llm, verdict, confidence, reasons,
                recommended_action, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                request_id, device_id, content_type, content, rule_score,
                json.dumps(matched_patterns), int(used_llm), verdict,
                confidence, json.dumps(reasons), recommended_action, _now(),
            ),
        )


def record_feedback(request_id: str, was_helpful: bool, actual_outcome: Optional[str]) -> None:
    with get_connection() as conn:
        conn.execute(
            """INSERT OR REPLACE INTO feedback (request_id, was_helpful, actual_outcome, created_at)
               VALUES (?, ?, ?, ?)""",
            (request_id, int(was_helpful), actual_outcome, _now()),
        )


def report_pattern(pattern_type: str, pattern_value: str) -> int:
    """Records that a pattern (e.g. a URL) showed up in a check. Returns
    the running report_count so callers can decide whether it's crossed
    a 'seen independently enough times to trust' threshold."""
    now = _now()
    with get_connection() as conn:
        conn.execute(
            """INSERT INTO threat_patterns (pattern_type, pattern_value, report_count, first_seen, last_seen)
               VALUES (?, ?, 1, ?, ?)
               ON CONFLICT(pattern_type, pattern_value)
               DO UPDATE SET report_count = report_count + 1, last_seen = excluded.last_seen""",
            (pattern_type, pattern_value, now, now),
        )
        row = conn.execute(
            "SELECT report_count FROM threat_patterns WHERE pattern_type = ? AND pattern_value = ?",
            (pattern_type, pattern_value),
        ).fetchone()
        return row["report_count"] if row else 1
