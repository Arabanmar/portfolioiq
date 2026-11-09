from piq_server import settings, summarizer

PLACEHOLDER_NOTE = "If volatilityAvailable is false, volatilityPart is a neutral placeholder"
CONFIDENCE = {"confidencePercent": 77.5, "confidenceLevel": "High"}
STOCKS = [{"ticker": "AAPL", "rating": "Buy", "decision": "d",
           "confidencePercent": 90.0, "confidenceLevel": "High"}]
ONE_LINE_REPLY = ('{"summary": "s", "strengths": ["a"], "weaknesses": ["b"], '
                  '"diversificationComment": "c"}')


def flat(text):
    return " ".join(text.split())


def capture_prompt(monkeypatch, reply):
    """Real mode with a stand-in for Gemini: remembers the prompt it was sent."""
    seen = {}

    def ask(prompt):
        seen["prompt"] = prompt
        return reply

    monkeypatch.setattr(settings, "FAKE_AI", False)
    monkeypatch.setattr(summarizer, "_ask_gemini", ask)
    return seen


def test_prompts_say_a_missing_volatility_is_only_a_placeholder():
    assert PLACEHOLDER_NOTE in summarizer.SUMMARY_PROMPT
    assert PLACEHOLDER_NOTE in summarizer.CHAT_PROMPT


def test_prompts_say_confidence_is_agreement_and_not_a_chance_of_profit():
    for prompt in (summarizer.SUMMARY_PROMPT, summarizer.CHAT_PROMPT):
        assert "how much the AI agents agree with each other" in flat(prompt)
        assert "not the chance of making money" in flat(prompt)
        assert "never describe it as a probability of profit or a guarantee" in flat(prompt)


def test_prompts_still_fill_in_without_stray_braces():
    summarizer.SUMMARY_PROMPT.format(metrics="{}", risk="{}", confidence="{}", decisions="")
    summarizer.CHAT_PROMPT.format(metrics="{}", risk="{}", confidence="{}", decisions="",
                                  summary="", question="?")


def test_decisions_text_shows_each_stocks_confidence():
    text = summarizer._decisions_text(STOCKS)
    assert "--- AAPL (rating: Buy, agent confidence: 90% High) ---" in text


def test_decisions_text_without_confidence_is_as_before():
    text = summarizer._decisions_text([{"ticker": "AAPL", "rating": "Buy", "decision": "d"}])
    assert "--- AAPL (rating: Buy) ---" in text


def test_summary_prompt_carries_the_portfolio_confidence(monkeypatch):
    seen = capture_prompt(monkeypatch, ONE_LINE_REPLY)

    result = summarizer.summarize({"totalValue": 1}, {"riskPercent": 50}, STOCKS, CONFIDENCE)

    assert result["summary"] == "s"
    assert '"confidencePercent": 77.5' in seen["prompt"]
    assert "agent confidence: 90% High" in seen["prompt"]


def test_summary_prompt_copes_with_no_confidence(monkeypatch):
    seen = capture_prompt(monkeypatch, ONE_LINE_REPLY)

    summarizer.summarize({"totalValue": 1}, {"riskPercent": 50}, [], None)

    assert "(JSON, percent is 10-95):\nnull\n" in seen["prompt"]
    assert "If it is null, confidence is not available: do not mention it." in seen["prompt"]


def test_both_prompts_say_what_to_do_when_confidence_is_null():
    for prompt in (summarizer.SUMMARY_PROMPT, summarizer.CHAT_PROMPT):
        assert "If it is null, confidence is not available: do not mention it." in flat(prompt)


def test_chat_prompt_carries_the_portfolio_confidence(monkeypatch):
    seen = capture_prompt(monkeypatch, "an answer")
    result = {"metrics": {}, "risk": {}, "stocks": STOCKS, "summary": {"summary": "x"},
              "confidence": CONFIDENCE}

    assert summarizer.answer("How sure are the agents?", result) == "an answer"
    assert '"confidencePercent": 77.5' in seen["prompt"]
