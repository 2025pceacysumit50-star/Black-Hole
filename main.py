"""
ShieldCheck backend — Model 2. Self-hosted: no API key, no third-party
LLM, your own SQLite database. Rules engine runs first and is instant;
Ollama (a locally-running LLM) reviews anything the rules can't
confidently call on their own. Same request/response shape as before,
so Model 1 (the Android app) needs zero changes to talk to this.

Run:
    pip install -r requirements.txt
    ollama serve  # separate terminal — see README.md
    uvicorn main:app --reload
"""

import uuid
from typing import List, Literal, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

import database
import llm_client
import ocr
import rules_engine

app = FastAPI(title="ShieldCheck Backend — Model 2 (self-hosted)")


@app.on_event("startup")
def startup() -> None:
    database.init_db()


class ScamCheckRequest(BaseModel):
    device_id: str
    type: Literal["text", "image", "call_transcript"]
    content: str
    source_app: Optional[str] = None


class ScamCheckResponse(BaseModel):
    request_id: str
    verdict: Literal["likely_scam", "suspicious", "likely_safe"]
    confidence: float
    reasons: List[str]
    recommended_action: str


class FeedbackRequest(BaseModel):
    was_helpful: bool
    actual_outcome: Optional[str] = None


class MalwareReportRequest(BaseModel):
    device_id: str
    package_name: str
    reason: str


class MalwareReportResponse(BaseModel):
    report_count: int
    widely_reported: bool


def _result(verdict: str, confidence: float, reasons: List[str], action: str) -> dict:
    return {
        "verdict": verdict,
        "confidence": confidence,
        "reasons": reasons,
        "recommended_action": action,
    }


@app.post("/v1/scam-check", response_model=ScamCheckResponse)
async def scam_check(req: ScamCheckRequest) -> ScamCheckResponse:
    request_id = str(uuid.uuid4())

    if req.type == "image":
        text = ocr.extract_text(req.content)
        if not text:
            result = _result(
                "suspicious", 0.3,
                ["Couldn't read any text in this image"],
                "Try sharing the message as text instead, if you can.",
            )
            return ScamCheckResponse(request_id=request_id, **result)
    else:
        text = req.content

    rules = rules_engine.evaluate(text)

    # The one piece of the cross-device network that's actually live:
    # a link reported by enough independent devices skips straight to a
    # confident verdict, no LLM call needed.
    for url in rules.urls:
        count = database.report_pattern("url", url)
        if count >= 3:
            result = _result(
                "likely_scam", 0.9,
                [f"This link has been reported by {count} different devices"],
                "Don't click this link or respond.",
            )
            database.log_check(
                request_id, req.device_id, req.type, text, rules.score,
                rules.matched_labels, False, result["verdict"],
                result["confidence"], result["reasons"], result["recommended_action"],
            )
            return ScamCheckResponse(request_id=request_id, **result)

    if rules.is_high_confidence_scam:
        result = _result(
            "likely_scam",
            min(0.6 + rules.score * 0.03, 0.95),
            rules.matched_labels,
            "This has several strong scam indicators — don't respond or click any links.",
        )
        used_llm = False
    else:
        try:
            result = llm_client.analyze(text, req.type, rules.matched_labels)
            used_llm = True
        except llm_client.OllamaUnavailable as e:
            raise HTTPException(status_code=503, detail=str(e))
        except (KeyError, ValueError) as e:
            raise HTTPException(status_code=502, detail=f"Model returned an unexpected shape: {e}")

    database.log_check(
        request_id, req.device_id, req.type, text, rules.score,
        rules.matched_labels, used_llm, result["verdict"],
        result["confidence"], result["reasons"], result["recommended_action"],
    )

    return ScamCheckResponse(request_id=request_id, **result)


@app.post("/v1/scam-check/{request_id}/feedback")
async def scam_check_feedback(request_id: str, feedback: FeedbackRequest) -> dict:
    database.record_feedback(request_id, feedback.was_helpful, feedback.actual_outcome)
    return {"status": "recorded"}


@app.post("/v1/malware-report", response_model=MalwareReportResponse)
async def malware_report(req: MalwareReportRequest) -> MalwareReportResponse:
    # Reuses the exact same counting mechanism already built for scam
    # links — "pattern_type" was made generic on purpose. This is the
    # piece that makes malware findings cross-device instead of
    # trapped on whichever phone happened to find them first.
    count = database.report_pattern("package", req.package_name)
    return MalwareReportResponse(report_count=count, widely_reported=count >= 3)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "storage": "sqlite (local)", "llm": "ollama (local)"}
