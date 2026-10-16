"""Ordered schema migrations; the database module applies them atomically."""

MIGRATIONS = (
    (
        1,
        (
            """CREATE TABLE sandbox_profiles (
                subject TEXT PRIMARY KEY CHECK (
                    length(subject) BETWEEN 9 AND 64
                    AND subject GLOB 'sandbox-*'
                    AND substr(subject, 9, 1) GLOB '[a-z0-9]'
                    AND substr(subject, 9) NOT GLOB '*[^a-z0-9_-]*'
                ),
                response_style TEXT NOT NULL CHECK (
                    response_style IN ('concise', 'step_by_step', 'detailed')
                )
            )""",
        ),
    ),
)
