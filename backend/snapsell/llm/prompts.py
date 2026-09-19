"""Prompt text for the three LLM tasks, loaded from versioned playbook files.

At module initialization, prompts are loaded from backend/playbooks/ and cached.
This file provides backward-compatible accessors for the prompt constants.
"""

from __future__ import annotations

from snapsell.config import get_settings
from snapsell.llm.playbooks import extract_section, load_playbooks

# Cache loaded playbooks at module level
_playbooks: dict | None = None
_load_error: Exception | None = None

# Module-level prompt constants (lazy-loaded)
IDENTIFY_SYSTEM: str
IDENTIFY_USER: str
IDENTIFY_USER_WITH_HINT: str
SOLD_SYSTEM: str
SOLD_USER: str
BUNDLE_SYSTEM: str
BUNDLE_USER: str


def _ensure_loaded() -> dict:
    """Load playbooks if not already loaded. Raises if loading failed."""
    global _playbooks, _load_error

    if _playbooks is not None:
        return _playbooks

    if _load_error is not None:
        raise RuntimeError(f"Failed to load playbooks at startup: {_load_error}") from _load_error

    try:
        settings = get_settings()
        playbooks_dir = settings.playbooks_dir or None
        _playbooks = load_playbooks(playbooks_dir)
        return _playbooks
    except Exception as e:
        _load_error = e
        raise RuntimeError(f"Failed to load playbooks at startup: {e}") from e


def _load_all_prompts() -> None:
    """Load all prompts from playbooks into module-level variables."""
    global IDENTIFY_SYSTEM, IDENTIFY_USER, IDENTIFY_USER_WITH_HINT
    global SOLD_SYSTEM, SOLD_USER, BUNDLE_SYSTEM, BUNDLE_USER

    playbooks = _ensure_loaded()

    if "identify" not in playbooks:
        raise RuntimeError("Required playbook 'identify' not found")
    if "sold_estimate" not in playbooks:
        raise RuntimeError("Required playbook 'sold_estimate' not found")
    if "bundle" not in playbooks:
        raise RuntimeError("Required playbook 'bundle' not found")

    identify_pb = playbooks["identify"]
    sold_pb = playbooks["sold_estimate"]
    bundle_pb = playbooks["bundle"]

    IDENTIFY_SYSTEM = extract_section(identify_pb.body, "System Prompt")
    IDENTIFY_USER = extract_section(identify_pb.body, "User Prompt")
    IDENTIFY_USER_WITH_HINT = extract_section(identify_pb.body, "User Prompt (with hint)")
    SOLD_SYSTEM = extract_section(sold_pb.body, "System Prompt")
    SOLD_USER = extract_section(sold_pb.body, "User Prompt Template")
    BUNDLE_SYSTEM = extract_section(bundle_pb.body, "System Prompt")
    BUNDLE_USER = extract_section(bundle_pb.body, "User Prompt Template")


# Load prompts at module import time
_load_all_prompts()
