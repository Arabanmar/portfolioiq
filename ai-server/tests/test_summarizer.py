from piq_server import summarizer

PLACEHOLDER_NOTE = "If volatilityAvailable is false, volatilityPart is a neutral placeholder"


def test_prompts_say_a_missing_volatility_is_only_a_placeholder():
    assert PLACEHOLDER_NOTE in summarizer.SUMMARY_PROMPT
    assert PLACEHOLDER_NOTE in summarizer.CHAT_PROMPT


def test_prompts_still_fill_in_without_stray_braces():
    summarizer.SUMMARY_PROMPT.format(metrics="{}", risk="{}", decisions="")
    summarizer.CHAT_PROMPT.format(metrics="{}", risk="{}", decisions="", summary="", question="?")
