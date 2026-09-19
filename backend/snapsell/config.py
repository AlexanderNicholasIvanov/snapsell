"""Runtime configuration. Every value comes from the environment, prefixed SNAPSELL_.

The Anthropic key is deliberately not modelled here: the SDK reads ANTHROPIC_API_KEY
itself, and keeping it out of Settings means it never appears in a repr or a log.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="SNAPSELL_", extra="ignore")

    claude_model: str = "claude-opus-5"
    # "medium" is enough for single-object identification and roughly halves the
    # cost of "high". Raise it if the accuracy log shows misidentifications.
    claude_effort: Literal["low", "medium", "high", "xhigh", "max"] = "medium"

    ebay_client_id: str = ""
    ebay_client_secret: str = ""
    ebay_env: Literal["production", "sandbox"] = "production"
    ebay_marketplace: str = "EBAY_US"
    # How many listings to pull for the median. eBay allows up to 200 per page.
    ebay_search_limit: int = Field(default=25, ge=5, le=200)
    # Below this many condition-filtered results we fall back to all conditions.
    min_filtered_comps: int = Field(default=3, ge=1)
    comps_shown: int = Field(default=5, ge=1, le=10)

    default_local_sale_factor: float = Field(default=0.85, gt=0, le=1.5)

    auth_disabled: bool = False
    allowed_emails: str = ""
    daily_cap: int = Field(default=200, ge=1)

    # Max decoded image size accepted by /identify.
    max_image_bytes: int = 4 * 1024 * 1024

    # Path to LLM prompt playbooks directory. Defaults to backend/playbooks/.
    playbooks_dir: str = ""

    @field_validator("allowed_emails")
    @classmethod
    def _normalise_emails(cls, value: str) -> str:
        return ",".join(e.strip().lower() for e in value.split(",") if e.strip())

    @property
    def allowed_email_set(self) -> frozenset[str]:
        return frozenset(e for e in self.allowed_emails.split(",") if e)

    @property
    def ebay_api_base(self) -> str:
        host = "api.sandbox.ebay.com" if self.ebay_env == "sandbox" else "api.ebay.com"
        return f"https://{host}"


@lru_cache
def get_settings() -> Settings:
    return Settings()
