"""Test playbook loading, parsing, and integrity.

Playbooks are versioned prompt files. This suite ensures:
1. All required playbooks load successfully
2. Each playbook has required metadata (name, version, purpose)
3. Prompt sections are correctly extracted
4. Prompt text matches pinned hashes (changes force deliberate version bumps)
"""

from __future__ import annotations

from pathlib import Path

import pytest

from snapsell.llm.playbooks import (
    extract_section,
    load_playbook,
    load_playbooks,
)

# Compute these once by running the tests, then hardcode them so any prompt edit
# forces a deliberate fixture update and version bump.
EXPECTED_PLAYBOOK_HASHES = {
    "identify": "475e22efe627e7b2efe27b9ed94866007eba0657f9c3410d52958c0e6bbdace1",
    "sold_estimate": "23a6ae10db4b4c40b131cd1f468c2ffcbeb32b8c3e865e98ddfe92d1e890c687",
    "bundle": "b87abf04682305b155997017f23e1286bbb714ef9c9dab8f2b7872a3c4905767",
}

# Expected section names per playbook (for validation)
EXPECTED_SECTIONS = {
    "identify": ["System Prompt", "User Prompt", "User Prompt (with hint)"],
    "sold_estimate": ["System Prompt", "User Prompt Template"],
    "bundle": ["System Prompt", "User Prompt Template"],
}


def _get_playbooks_dir() -> Path:
    """Return path to playbooks directory."""
    return Path(__file__).parent.parent / "playbooks"


class TestPlaybookLoading:
    """Test basic playbook file loading and parsing."""

    def test_load_identify_playbook(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "identify.md")
        assert pb.name == "identify"
        assert pb.version == 1
        assert pb.purpose is not None
        assert len(pb.body) > 0

    def test_load_sold_estimate_playbook(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "sold_estimate.md")
        assert pb.name == "sold_estimate"
        assert pb.version == 1
        assert pb.purpose is not None
        assert len(pb.body) > 0

    def test_load_bundle_playbook(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "bundle.md")
        assert pb.name == "bundle"
        assert pb.version == 1
        assert pb.purpose is not None
        assert len(pb.body) > 0

    def test_load_all_playbooks(self) -> None:
        playbook_dir = _get_playbooks_dir()
        playbooks = load_playbooks(playbook_dir)
        assert len(playbooks) == 3
        assert "identify" in playbooks
        assert "sold_estimate" in playbooks
        assert "bundle" in playbooks


class TestPlaybookIntegrity:
    """Test that playbook content hasn't changed unintentionally."""

    def test_identify_sha256_unchanged(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "identify.md")
        expected = EXPECTED_PLAYBOOK_HASHES["identify"]
        assert pb.sha256 == expected, (
            f"identify prompt changed: bump version and update recorded fixtures. "
            f"old={expected}, new={pb.sha256}"
        )

    def test_sold_estimate_sha256_unchanged(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "sold_estimate.md")
        expected = EXPECTED_PLAYBOOK_HASHES["sold_estimate"]
        assert pb.sha256 == expected, (
            f"sold_estimate prompt changed: bump version and update recorded fixtures. "
            f"old={expected}, new={pb.sha256}"
        )

    def test_bundle_sha256_unchanged(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "bundle.md")
        expected = EXPECTED_PLAYBOOK_HASHES["bundle"]
        assert pb.sha256 == expected, (
            f"bundle prompt changed: bump version and update recorded fixtures. "
            f"old={expected}, new={pb.sha256}"
        )


class TestSectionExtraction:
    """Test extraction of prompt sections from playbook bodies."""

    def test_extract_identify_system_prompt(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "identify.md")
        system = extract_section(pb.body, "System Prompt")
        assert len(system) > 0
        assert "second-hand items" in system

    def test_extract_identify_user_prompt(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "identify.md")
        user = extract_section(pb.body, "User Prompt")
        assert "Identify this item" in user

    def test_extract_identify_user_prompt_with_hint(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "identify.md")
        user = extract_section(pb.body, "User Prompt (with hint)")
        assert "seller says" in user.lower()

    def test_extract_sold_system_prompt(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "sold_estimate.md")
        system = extract_section(pb.body, "System Prompt")
        assert "second-hand item" in system

    def test_extract_sold_user_template(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "sold_estimate.md")
        user = extract_section(pb.body, "User Prompt Template")
        assert "{name}" in user
        assert "{brand}" in user

    def test_extract_bundle_system_prompt(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "bundle.md")
        system = extract_section(pb.body, "System Prompt")
        assert "Facebook Marketplace" in system

    def test_extract_bundle_user_template(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "bundle.md")
        user = extract_section(pb.body, "User Prompt Template")
        assert "{bundle_price:.0f}" in user
        assert "{items}" in user

    def test_extract_nonexistent_section_raises(self) -> None:
        playbook_dir = _get_playbooks_dir()
        pb = load_playbook(playbook_dir / "identify.md")
        with pytest.raises(ValueError, match="Section.*not found"):
            extract_section(pb.body, "Nonexistent Section")


class TestPlaybookModuleLoading:
    """Test that prompts.py loads playbooks at import time."""

    def test_prompts_module_loads_identify_system(self) -> None:
        # Re-import to trigger module load. In a real scenario, this is already done.
        from snapsell.llm import prompts

        assert prompts.IDENTIFY_SYSTEM is not None
        assert len(prompts.IDENTIFY_SYSTEM) > 0
        assert "second-hand items" in prompts.IDENTIFY_SYSTEM

    def test_prompts_module_loads_identify_user(self) -> None:
        from snapsell.llm import prompts

        assert prompts.IDENTIFY_USER is not None
        assert "Identify this item" in prompts.IDENTIFY_USER

    def test_prompts_module_loads_identify_user_with_hint(self) -> None:
        from snapsell.llm import prompts

        assert prompts.IDENTIFY_USER_WITH_HINT is not None
        assert "seller says" in prompts.IDENTIFY_USER_WITH_HINT.lower()

    def test_prompts_module_loads_sold_system(self) -> None:
        from snapsell.llm import prompts

        assert prompts.SOLD_SYSTEM is not None
        assert "second-hand item" in prompts.SOLD_SYSTEM

    def test_prompts_module_loads_sold_user(self) -> None:
        from snapsell.llm import prompts

        assert prompts.SOLD_USER is not None
        assert "{name}" in prompts.SOLD_USER

    def test_prompts_module_loads_bundle_system(self) -> None:
        from snapsell.llm import prompts

        assert prompts.BUNDLE_SYSTEM is not None
        assert "Facebook Marketplace" in prompts.BUNDLE_SYSTEM

    def test_prompts_module_loads_bundle_user(self) -> None:
        from snapsell.llm import prompts

        assert prompts.BUNDLE_USER is not None
        assert "{bundle_price:.0f}" in prompts.BUNDLE_USER


class TestMissingPlaybook:
    """Test error handling for missing playbooks."""

    def test_load_from_nonexistent_directory(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            nonexistent = Path(tmpdir) / "nonexistent"
            with pytest.raises(FileNotFoundError):
                load_playbooks(nonexistent)

    def test_malformed_playbook_raises_error(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            bad_file = Path(tmpdir) / "bad.md"
            bad_file.write_text("no front matter here")
            with pytest.raises(ValueError, match="must start with"):
                load_playbook(bad_file)
