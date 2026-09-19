"""Load and manage versioned LLM prompt playbooks from markdown files.

Each playbook file contains YAML front matter (name, version, purpose) and a body
with prompt sections marked by ## headers. Playbooks are loaded at startup and
cached in memory.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Playbook:
    """A versioned prompt playbook with content hash for integrity."""

    name: str
    version: int
    purpose: str
    body: str
    sha256: str


def _parse_markdown_front_matter(content: str) -> tuple[dict[str, Any], str]:
    """Parse YAML front matter and return (metadata, body).

    Expects content to start with --- , followed by YAML key: value lines,
    then another --- line, then the body.
    """
    if not content.startswith("---"):
        raise ValueError("Playbook must start with ---")

    lines = content.split("\n")
    # Find the closing ---
    closing_idx = None
    for i in range(1, len(lines)):
        if lines[i].strip() == "---":
            closing_idx = i
            break

    if closing_idx is None:
        raise ValueError("Playbook front matter not closed with ---")

    # Parse the YAML manually (no external dependency)
    metadata = {}
    for line in lines[1:closing_idx]:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if ":" in line:
            key, value = line.split(":", 1)
            key = key.strip()
            value = value.strip()
            # Parse value: remove quotes if present, convert numbers
            if value.lower() in ("true", "false"):
                metadata[key] = value.lower() == "true"
            elif value.isdigit():
                metadata[key] = int(value)
            else:
                # Remove quotes if present
                if value.startswith('"') and value.endswith('"'):
                    value = value[1:-1]
                elif value.startswith("'") and value.endswith("'"):
                    value = value[1:-1]
                metadata[key] = value

    body = "\n".join(lines[closing_idx + 1 :]).lstrip("\n")
    return metadata, body


def load_playbook(path: Path) -> Playbook:
    """Load a single playbook from a markdown file.

    Args:
        path: Path to the .md file

    Returns:
        Playbook with parsed metadata and body

    Raises:
        ValueError: If the file is malformed or missing required fields
    """
    content = path.read_text(encoding="utf-8")
    metadata, body = _parse_markdown_front_matter(content)

    # Validate required fields
    if "name" not in metadata:
        raise ValueError(f"{path.name}: missing 'name' in front matter")
    if "version" not in metadata:
        raise ValueError(f"{path.name}: missing 'version' in front matter")
    if "purpose" not in metadata:
        raise ValueError(f"{path.name}: missing 'purpose' in front matter")

    # Compute SHA256 of the body (the actual prompt text)
    sha256 = hashlib.sha256(body.encode("utf-8")).hexdigest()

    return Playbook(
        name=metadata["name"],
        version=metadata["version"],
        purpose=metadata["purpose"],
        body=body,
        sha256=sha256,
    )


def load_playbooks(directory: str | Path | None = None) -> dict[str, Playbook]:
    """Load all playbook files from a directory.

    Args:
        directory: Path to the playbooks directory. If None, defaults to
                   the 'playbooks' directory adjacent to this module.

    Returns:
        Dict mapping playbook name -> Playbook

    Raises:
        FileNotFoundError: If the directory doesn't exist
        ValueError: If any playbook file is malformed
    """
    if directory is None:
        # Default to playbooks/ at the backend root
        directory = Path(__file__).parent.parent.parent / "playbooks"
    else:
        directory = Path(directory)

    if not directory.exists():
        raise FileNotFoundError(f"Playbooks directory not found: {directory}")

    playbooks: dict[str, Playbook] = {}
    for md_file in sorted(directory.glob("*.md")):
        try:
            pb = load_playbook(md_file)
            playbooks[pb.name] = pb
        except (ValueError, OSError) as e:
            raise ValueError(f"Failed to load playbook {md_file.name}: {e}") from e

    return playbooks


def extract_section(playbook_body: str, section_name: str) -> str:
    """Extract a section from a playbook body by header.

    Args:
        playbook_body: The full body text
        section_name: The section to find (e.g., "System Prompt")

    Returns:
        The section content (without the ## header), stripped

    Raises:
        ValueError: If the section is not found
    """
    lines = playbook_body.split("\n")
    section_lines = []
    in_section = False

    for line in lines:
        # Check if this is a header line
        if line.strip().startswith("##"):
            if in_section:
                # We've hit the next section, stop
                break
            # Check if this is the section we want
            header_text = line.strip()[2:].strip()  # Remove ## and whitespace
            if header_text.lower() == section_name.lower():
                in_section = True
            continue

        if in_section:
            section_lines.append(line)

    if not in_section:
        raise ValueError(f"Section '{section_name}' not found in playbook body")

    return "\n".join(section_lines).strip()
