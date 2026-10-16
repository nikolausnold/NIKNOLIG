"""Transactional versioned SQLite storage containing only synthetic preferences."""

import sqlite3
from contextlib import contextmanager
from pathlib import Path

from .config import SUBJECT
from .migrations import MIGRATIONS

RESPONSE_STYLES = frozenset({"concise", "step_by_step", "detailed"})


def validate_subject(subject):
    if not isinstance(subject, str) or SUBJECT.fullmatch(subject) is None:
        raise ValueError("invalid synthetic subject")


def validate_profile(profile):
    if (
        not isinstance(profile, dict)
        or set(profile) != {"responseStyle"}
        or not isinstance(profile["responseStyle"], str)
        or profile["responseStyle"] not in RESPONSE_STYLES
    ):
        raise ValueError("invalid profile")
    return profile["responseStyle"]


class Database:
    def __init__(self, path):
        self.path = str(path)

    @contextmanager
    def connect(self):
        connection = sqlite3.connect(self.path, timeout=1.0)
        try:
            connection.execute("PRAGMA busy_timeout = 1000")
            connection.execute("PRAGMA foreign_keys = ON")
            with connection:
                yield connection
        finally:
            connection.close()

    def migrate(self, migrations=None):
        migrations = MIGRATIONS if migrations is None else migrations
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as connection:
            try:
                connection.execute("BEGIN IMMEDIATE")
                connection.execute(
                    "CREATE TABLE IF NOT EXISTS schema_migrations "
                    "(version INTEGER PRIMARY KEY)"
                )
                applied = [
                    row[0] for row in connection.execute(
                        "SELECT version FROM schema_migrations ORDER BY version"
                    )
                ]
                versions = [version for version, _ in migrations]
                if versions != list(range(1, len(versions) + 1)):
                    raise ValueError("invalid migration sequence")
                if applied != versions[:len(applied)]:
                    raise ValueError("unsupported schema version")
                for version, statements in migrations[len(applied):]:
                    for statement in statements:
                        connection.execute(statement)
                    connection.execute(
                        "INSERT INTO schema_migrations(version) VALUES (?)", (version,)
                    )
                connection.commit()
            except Exception:
                connection.rollback()
                raise

    def ready(self):
        try:
            with self.connect() as connection:
                versions = [
                    row[0] for row in connection.execute(
                        "SELECT version FROM schema_migrations ORDER BY version"
                    )
                ]
                connection.execute(
                    "SELECT subject, response_style FROM sandbox_profiles LIMIT 0"
                )
                return versions == [version for version, _ in MIGRATIONS]
        except sqlite3.Error:
            return False

    def get_profile(self, subject):
        validate_subject(subject)
        with self.connect() as connection:
            row = connection.execute(
                "SELECT response_style FROM sandbox_profiles WHERE subject = ?",
                (subject,),
            ).fetchone()
        if row is None:
            return None
        profile = {"responseStyle": row[0]}
        validate_profile(profile)
        return profile

    def put_profile(self, subject, profile):
        validate_subject(subject)
        style = validate_profile(profile)
        with self.connect() as connection:
            connection.execute(
                "INSERT INTO sandbox_profiles(subject, response_style) VALUES (?, ?) "
                "ON CONFLICT(subject) DO UPDATE SET response_style = excluded.response_style",
                (subject, style),
            )
        return {"responseStyle": style}

    def delete_profile(self, subject):
        validate_subject(subject)
        with self.connect() as connection:
            connection.execute(
                "DELETE FROM sandbox_profiles WHERE subject = ?", (subject,)
            )
