from piq_server.debate import DEBATE_CASE_CHARS, case_text, first_argument, stock_debate

BULL = "\nBull Analyst: **Growth is strong.** Shares have [room to run](https://x.example)."
BEAR = "\nBear Analyst: ## Risks\n\n- Costs are rising\n- Demand is slowing"


def state_with(bull=None, bear=None):
    return {"investment_debate_state": {"bull_history": bull, "bear_history": bear}}


# ---- reading both sides ----

def test_both_sides_are_read_with_their_labels_removed_and_markdown_gone():
    assert stock_debate(state_with(BULL, BEAR)) == {
        "bullCase": "Growth is strong. Shares have room to run.",
        "bearCase": "Risks\nCosts are rising\nDemand is slowing",
    }


def test_each_side_is_read_from_its_own_history():
    assert stock_debate(state_with(bull=BULL))["bearCase"] == ""
    assert stock_debate(state_with(bear=BEAR))["bullCase"] == ""


def test_the_limit_is_500_characters():
    assert DEBATE_CASE_CHARS == 500


# ---- trimming ----

def test_a_long_argument_is_cut_to_at_most_500_characters_and_ends_with_dots():
    history = "\nBull Analyst: " + "Shares rose on strong demand. " * 60
    result = case_text(history, "Bull")
    assert 0 < len(result) <= 500
    assert result.endswith("...")
    assert result.startswith("Shares rose on strong demand.")


def test_an_argument_of_exactly_500_characters_is_unchanged():
    assert case_text("\nBull Analyst: " + "x" * 500, "Bull") == "x" * 500


def test_an_argument_of_501_characters_is_cut_to_500():
    result = case_text("\nBull Analyst: " + "x" * 501, "Bull")
    assert len(result) == 500
    assert result.endswith("...")


def test_a_short_argument_is_kept_whole():
    assert case_text("\nBear Analyst: Short.", "Bear") == "Short."


def test_the_cut_never_splits_a_word():
    history = "\nBear Analyst: " + " ".join(f"word{i:03d}" for i in range(200))
    result = case_text(history, "Bear")
    assert len(result) <= 500
    kept = result[:-3].split(" ")
    assert all(word.startswith("word") and len(word) == 7 for word in kept)


# ---- only the first argument ----

def test_only_the_first_argument_is_used_when_there_are_several_rounds():
    assert case_text("\nBull Analyst: First point.\nBull Analyst: Second point.", "Bull") == "First point."
    assert case_text("\nBear Analyst: One.\nBear Analyst: Two.\nBear Analyst: Three.", "Bear") == "One."


def test_a_blank_first_argument_falls_through_to_the_next():
    assert case_text("\nBull Analyst:\nBull Analyst: Real point.", "Bull") == "Real point."


def test_a_label_inside_a_sentence_does_not_split_the_argument():
    assert case_text("\nBull Analyst: He said Bull Analyst: stay.", "Bull") == "He said Bull Analyst: stay."


def test_a_label_the_model_repeated_at_the_start_of_its_reply_is_dropped():
    assert case_text("\nBear Analyst: Bear Analyst: Costs.", "Bear") == "Costs."
    assert case_text("\nBear Analyst: **Bear Analyst:** Costs.", "Bear") == "Costs."
    assert case_text("\nBull Analyst: Bull Analyst: Bull Analyst: Growth.", "Bull") == "Growth."


def test_a_label_later_in_the_reply_is_kept():
    assert case_text("\nBull Analyst: Growth. Bull Analyst: is what I said.", "Bull") == \
        "Growth. Bull Analyst: is what I said."


def test_windows_line_endings_are_read():
    assert case_text("\r\nBull Analyst: x", "Bull") == "x"
    assert case_text("\r\nBull Analyst: First.\r\nMore.\r\nBull Analyst: Second.", "Bull") == "First.\nMore."


def test_the_label_is_read_ignoring_case_and_spacing():
    assert case_text("\nbull   analyst :  Growth.", "Bull") == "Growth."
    assert case_text("BEAR ANALYST:Costs.", "Bear") == "Costs."


def test_a_history_without_a_label_is_taken_whole():
    assert first_argument("Just an argument.", "Bull") == "Just an argument."
    assert case_text("Just an argument.", "Bull") == "Just an argument."


def test_the_other_sides_label_does_not_count():
    assert first_argument("\nBear Analyst: Costs.", "Bull") == "\nBear Analyst: Costs."


# ---- empty, missing or odd input ----

def test_an_empty_or_missing_history_gives_no_case():
    for history in (None, "", "   \n", "\nBull Analyst:", "\nBull Analyst:  \n"):
        assert case_text(history, "Bull") == ""


def test_a_history_given_as_content_parts_is_read():
    parts = [{"type": "text", "text": "\nBull Analyst: Growth."}]
    assert case_text(parts, "Bull") == "Growth."
    assert case_text([], "Bull") == ""


def test_a_missing_or_odd_debate_state_gives_no_cases():
    empty = {"bullCase": "", "bearCase": ""}
    for state in ({}, None, "text", [], {"investment_debate_state": None},
                  {"investment_debate_state": "text"}, {"investment_debate_state": {}}):
        assert stock_debate(state) == empty
