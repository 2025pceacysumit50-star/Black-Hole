# ShieldCheck Backend — Model 2 (self-hosted)

No API key. No third-party LLM. Your own database. This replaces the
earlier Anthropic-based backend — Model 1 (the Android app) needs zero
changes, since it just calls `/v1/scam-check` and doesn't know or care
what's running behind that URL.

## How a check actually gets decided

1. **Rules engine** (instant, no compute) — scores the text against known
   scam patterns. A high score returns immediately; no LLM call needed.
2. **Known-bad links** (instant) — if a link in the message has already
   been reported 3+ times by different devices, that's returned as an
   immediate verdict too. This is the one piece of the "connects all
   devices" idea that's actually live right now.
3. **Local LLM** (a few seconds to under a minute on CPU) — anything the
   rules can't confidently call goes to Ollama, running entirely on your
   own machine, with the rules engine's findings passed along as hints.

Everything gets logged to `shieldcheck.db` (plain SQLite, one file, yours).

## Windows-specific notes (found from a real run)

- **Drop `--reload`.** `uvicorn main:app --reload` crashes on Windows +
  Python 3.14 with a multiprocessing bootstrapping error — a known clash
  between uvicorn's file-watcher and Windows' spawn-based process model,
  not a bug in this code. Just run `uvicorn main:app --host 0.0.0.0`
  instead; you'll restart it manually after code changes, which is fine
  for now.
- **`curl -fsSL ... | sh` won't work** — that's the Linux/Mac installer;
  `sh` doesn't exist on Windows. Use the installer from
  ollama.com/download instead.
- **If `ollama serve` says the address is already in use**, that's not an
  error — it means Ollama's already running in the background (its
  Windows installer sets it up that way). Skip that command entirely.
- **`sudo apt install` doesn't exist on Windows.** Download the Tesseract
  installer from the UB-Mannheim GitHub releases page instead, run it,
  and make sure `tesseract.exe`'s folder is on your PATH (or set
  `pytesseract.pytesseract.tesseract_cmd` to its full path directly in
  `ocr.py` if you'd rather not touch PATH).
- **PowerShell's `curl` isn't real curl** — it's an alias for
  `Invoke-WebRequest`, which doesn't understand `-X`, `-H`, or `-d`.
  Call `curl.exe` explicitly to get the actual curl:
  ```powershell
  curl.exe -X POST http://localhost:8000/v1/scam-check -H "Content-Type: application/json" -d '{"device_id":"test","type":"text","content":"Your account will be suspended within 24 hours. Verify now: bit.ly/xyz123"}'
  ```
  Or use PowerShell's own cmdlet instead:
  ```powershell
  Invoke-RestMethod -Uri "http://localhost:8000/v1/scam-check" -Method Post -ContentType "application/json" -Body '{"device_id":"test","type":"text","content":"Your account will be suspended within 24 hours. Verify now: bit.ly/xyz123"}'
  ```

## Setup

Commands below are Linux/Mac-flavored — which is also exactly what you'll
run on Oracle's server later, so it's not wasted if you're on Windows now.
Swap in the Windows equivalents from the notes above as you go.

**1. Install Ollama** (the thing that runs the model locally):

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Works on both your laptop (16GB RAM — comfortable) and Oracle's ARM free
tier (12GB — tighter, but fine for the model size below).

**2. Pull a model:**

```bash
ollama pull llama3.2:3b
```

Starts small deliberately — fast enough on CPU that a check doesn't feel
like a hang. Run `ollama list` to confirm it downloaded. Want more
nuance and don't mind slower responses? Pull a bigger one (`llama3.1:8b`,
`qwen2.5:7b`) and change `MODEL_NAME` in `llm_client.py` — one line.

**3. Install Tesseract** (system package, for reading screenshots):

```bash
sudo apt install tesseract-ocr
```

**4. Install Python dependencies and run:**

```bash
pip install -r requirements.txt
ollama serve          # separate terminal, leave running
uvicorn main:app --reload
```

## Test it

```bash
curl -X POST http://localhost:8000/v1/scam-check \
  -H "Content-Type: application/json" \
  -d '{"device_id":"test123","type":"text","content":"Your account will be suspended within 24 hours. Verify now: bit.ly/xyz123"}'
```

That example should come back as `likely_scam` from the rules engine
alone, instantly — no LLM needed, so it's a good first test even before
Ollama is running. Try a genuinely ambiguous message next to see the LLM
path kick in (that one will take a few seconds).

## Deploying to Oracle's free tier

Same steps as above, run on the Oracle ARM instance instead of your
laptop. Ollama has native ARM64 builds, so this isn't a problem. Point
the Android app's `baseUrl` at the instance's public IP once it's up.
Keep your laptop as the place you develop and test against — no reason
to redeploy to Oracle for every small change.

## What's honestly not done yet

- **No push notifications.** The link-tracking table counts reports
  across devices, but nothing actively alerts other users yet — that
  needs real traffic to be worth building, and right now there isn't
  any. The counting logic is there and tested; the alerting isn't.
- **No data retention policy.** Full message content is stored in
  `shieldcheck.db` for now, which is exactly what you'd want if you
  ever fine-tune your own classifier on real data later — but it also
  means you need an actual policy (how long you keep it, what you tell
  users) before this reaches real people. That's a privacy-policy
  question as much as a technical one.
- **Not integration-tested end to end.** `fastapi` isn't installed in
  the environment this was built in and there's no network access to
  install it, so `main.py` itself has only been syntax-checked, not run.
  Everything it calls — the rules engine, the database logic, the JSON
  parsing — was actually executed and verified against real inputs
  during the build, not just checked for typos. Run the `curl` test
  above before trusting the whole thing.
