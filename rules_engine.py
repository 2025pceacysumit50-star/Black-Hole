"""
Fast, zero-compute first pass. Runs before any LLM call — catches obvious
cases instantly and hands the LLM useful hints for everything else. This
is pattern matching, not a learned model: it won't catch scams that avoid
these specific words, which is exactly why it hands off to the LLM rather
than being the only line of defense.
"""

import re
from dataclasses import dataclass, field

URGENCY_PATTERNS = [
    r"act now", r"immediately", r"within 24 hours", r"urgent(ly)?",
    r"account (will be |has been )?(suspended|locked|closed|deactivated)",
    r"verify your account", r"confirm your identity", r"unusual activity",
    r"final (notice|warning)",
]

FINANCIAL_ASK_PATTERNS = [
    r"gift card", r"wire transfer", r"\bbitcoin\b", r"crypto(currency)?",
    r"send money", r"processing fee", r"claim your (prize|reward)",
    r"you('ve| have) won", r"\blottery\b", r"inheritance",
    r"provide your (card|otp|pin)",
]

IMPERSONATION_PATTERNS = [
    r"\birs\b", r"income tax department", r"arrest warrant",
    r"tax refund", r"account.{0,20}(suspended|blocked|frozen)",
    r"customs (department|duty)",
]

URL_PATTERN = re.compile(r"https?://\S+|www\.\S+")
SHORTENED_URL_PATTERN = re.compile(
    r"(bit\.ly|tinyurl\.com|t\.co|goo\.gl|is\.gd|cutt\.ly)/\S+", re.IGNORECASE
)

RULE_GROUPS = [
    (URGENCY_PATTERNS, 2, "urgency language"),
    (FINANCIAL_ASK_PATTERNS, 3, "requests money, gift cards, or financial info"),
    (IMPERSONATION_PATTERNS, 3, "impersonates a bank or government agency"),
]

HIGH_CONFIDENCE_THRESHOLD = 8


@dataclass
class RuleResult:
    score: int
    matched_labels: list[str] = field(default_factory=list)
    urls: list[str] = field(default_factory=list)
    has_shortened_url: bool = False

    @property
    def is_high_confidence_scam(self) -> bool:
        return self.score >= HIGH_CONFIDENCE_THRESHOLD


def evaluate(text: str) -> RuleResult:
    lowered = text.lower()
    score = 0
    matched_labels = []

    for patterns, weight, label in RULE_GROUPS:
        hits = sum(1 for p in patterns if re.search(p, lowered))
        if hits:
            score += hits * weight
            matched_labels.append(f"{label} ({hits} match{'es' if hits > 1 else ''})")

    urls = set(URL_PATTERN.findall(text))
    # findall() on SHORTENED_URL_PATTERN would only return the captured
    # domain group, not the full link — finditer + group(0) gets the
    # whole bit.ly/xyz123 span, which is what's actually worth tracking.
    shortened_spans = [m.group(0) for m in SHORTENED_URL_PATTERN.finditer(text)]
    if shortened_spans:
        score += 2
        matched_labels.append("uses a link-shortening service")
        urls.update(shortened_spans)

    return RuleResult(
        score=score,
        matched_labels=matched_labels,
        urls=sorted(urls),
        has_shortened_url=bool(shortened_spans),
    )
