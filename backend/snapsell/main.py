"""FastAPI application.

`create_app` takes the LLM and eBay dependencies as arguments so tests can inject
fixture-backed fakes. The module-level `app` is what uvicorn runs.
"""

from __future__ import annotations

import base64
import binascii
import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from snapsell.auth import DailyCap, User, current_user, metered_user
from snapsell.config import Settings, get_settings
from snapsell.ebay.client import EbayClient, EbayError, SearchSource
from snapsell.llm.client import ClaudeClient, LlmClient, LlmError, LlmRefused
from snapsell.models import (
    BundleRequest,
    BundleResponse,
    IdentifyRequest,
    IdentifyResponse,
    PriceQuote,
    PriceRequest,
)
from snapsell.pricing.service import PricingService
from snapsell.pricing.sold import LlmSoldEstimate, SoldPriceSource

log = logging.getLogger("snapsell")


def new_request_id() -> str:
    return "req_" + uuid.uuid4().hex[:16]


def create_app(
    settings: Settings | None = None,
    llm: LlmClient | None = None,
    ebay: SearchSource | None = None,
    sold: SoldPriceSource | None = None,
) -> FastAPI:
    settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        # Build real clients lazily so importing the app never needs credentials.
        app.state.settings = settings
        app.state.daily_cap = DailyCap(settings.daily_cap)
        app.state.llm = llm or ClaudeClient(settings.claude_model, settings.claude_effort)
        app.state.ebay = ebay or EbayClient(
            settings.ebay_client_id,
            settings.ebay_client_secret,
            settings.ebay_api_base,
            settings.ebay_marketplace,
        )
        app.state.pricing = PricingService(
            app.state.ebay, sold or LlmSoldEstimate(app.state.llm), settings
        )
        if settings.auth_disabled:
            log.warning("SNAPSELL_AUTH_DISABLED is set: every request is treated as the dev user")
        yield
        aclose = getattr(app.state.ebay, "aclose", None)
        if aclose:
            await aclose()

    app = FastAPI(title="SnapSell backend", version="0.1.0", lifespan=lifespan)

    @app.exception_handler(LlmRefused)
    async def _refused(_: Request, exc: LlmRefused) -> JSONResponse:
        return JSONResponse(status_code=422, content={"detail": str(exc)})

    @app.exception_handler(LlmError)
    async def _llm_error(_: Request, exc: LlmError) -> JSONResponse:
        log.error("llm error: %s", exc)
        return JSONResponse(status_code=502, content={"detail": "identification service failed"})

    @app.exception_handler(EbayError)
    async def _ebay_error(_: Request, exc: EbayError) -> JSONResponse:
        log.error("ebay error: %s", exc)
        return JSONResponse(status_code=502, content={"detail": "pricing service failed"})

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/identify", response_model=IdentifyResponse)
    async def identify(
        body: IdentifyRequest, request: Request, user: User = Depends(metered_user)
    ) -> IdentifyResponse:
        try:
            image = base64.b64decode(body.image_base64, validate=True)
        except (binascii.Error, ValueError) as e:
            raise HTTPException(status_code=400, detail="image_base64 is not valid base64") from e
        if not image:
            raise HTTPException(status_code=400, detail="image is empty")
        if len(image) > request.app.state.settings.max_image_bytes:
            raise HTTPException(status_code=413, detail="image too large")
        request_id = new_request_id()
        log.info("identify request_id=%s uid=%s bytes=%d", request_id, user.uid, len(image))
        result = await request.app.state.llm.identify(image, body.media_type, body.hint)
        return IdentifyResponse(
            item=result.item, listing_text=result.listing_text, request_id=request_id
        )

    @app.post("/price", response_model=PriceQuote)
    async def price(
        body: PriceRequest, request: Request, user: User = Depends(metered_user)
    ) -> PriceQuote:
        request_id = new_request_id()
        log.info(
            "price request_id=%s uid=%s query=%r", request_id, user.uid, body.item.search_query
        )
        return await request.app.state.pricing.quote(body.item, body.local_sale_factor, request_id)

    @app.post("/bundle", response_model=BundleResponse)
    async def bundle(
        body: BundleRequest, request: Request, user: User = Depends(metered_user)
    ) -> BundleResponse:
        request_id = new_request_id()
        log.info("bundle request_id=%s uid=%s items=%d", request_id, user.uid, len(body.items))
        text = await request.app.state.llm.bundle_text(body.items, body.bundle_price)
        return BundleResponse(title=text.title, description=text.description, request_id=request_id)

    @app.get("/me")
    async def me(user: User = Depends(current_user)) -> dict[str, str | None]:
        """Lets the app confirm the token and allowlist before spending anything."""
        return {"uid": user.uid, "email": user.email}

    return app


app = create_app()
