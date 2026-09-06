"""
Calls a locally-running Ollama instance — no API key, no third-party
service, no per-request cost. Install Ollama and pull a model first;
see README.md.
"""

import json

import requests

OLLAMA_URL = "http://localhost:11434/api/generate"

# Starts small on purpose — llama3.2:3b is fast enough on CPU that a
# check doesn't feel like a hang. Swap for a bigger model (llama3.1:8b,
# qwen2.5:7b, mistral:7b) once Ollama is running if you want more
# nuance and don't mind a slower response; it's a one-line change.
# Run `ollama list` to see what you've actually pulled.
MODEL_NAME = "llama3.2:3b"

SYSTEM_PROMPT = """You are a scam-detection assistant. A user has submitted a message, \
screenshot, or call transcript because they're worried it might be a scam.

Look for: impersonation of banks/government/companies, urgency or fear tactics, \
requests for money/gift cards/crypto/personal or financial info, suspicious links, \
and phrasing inconsistent with how a legitimate sender would actually communicate.

Respond with ONLY a JSON object, no other text, in exactly this shape:
{"verdict": "likely_scam" | "suspicious" | "likely_safe", "confidence": 0.0-1.0, \
"reasons": ["short reason 1", "short reason 2"], "recommended_action": "one short, \
plain-language sentence"}"""


class OllamaUnavailable(Exception):
    pass


def analyze(content: str, content_type: str, rule_hints: list[str]) -> dict:
    hint_text = ""
    if rule_hints:
        hint_text = (
            f"\n\nAutomated pattern matching already flagged: {'; '.join(rule_hints)}. "
            "Weigh this alongside your own reading of the content."
        )

    prompt = f"{SYSTEM_PROMPT}\n\nAnalyze this {content_type}:{hint_text}\n\n{content}"

    try:
        response = requests.post(
            OLLAMA_URL,
            json={
                "model": MODEL_NAME,
                "prompt": prompt,
                "stream": False,
                "format": "json",
                "options": {"temperature": 0.2},
            },
            timeout=90,
        )
        response.raise_for_status()
    except requests.exceptions.ConnectionError as e:
        raise OllamaUnavailable(
            "Can't reach Ollama at localhost:11434. Is it running? Try `ollama serve`."
        ) from e

    raw_text = response.json()["response"].strip()
    raw_text = raw_text.replace("```json", "").replace("```", "").strip()
    return json.loads(raw_text)
