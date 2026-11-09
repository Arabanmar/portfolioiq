from piq_server.text import ELLIPSIS, as_text, plain_text, shorten


# ---- as_text ----

def test_as_text_reads_strings_none_numbers_and_lists_of_parts():
    assert as_text("abc") == "abc"
    assert as_text(None) == ""
    assert as_text(5) == "5"
    assert as_text([{"type": "text", "text": "one"}, "two", {"type": "image"}]) == "one\ntwo\n"
    assert as_text([[{"text": "nested"}]]) == "nested"
    assert as_text([]) == ""


# ---- plain_text ----

def test_heading_and_bold_marks_are_removed():
    assert plain_text("## Market overview\n\n**Apple** rose _sharply_ today.") == \
        "Market overview\nApple rose sharply today."


def test_links_keep_their_text_and_lose_their_address():
    assert plain_text("Reported by [Reuters](https://example.com/a?b=1) and [AP](http://x).") == \
        "Reported by Reuters and AP."


def test_bullets_numbers_and_quotes_lose_their_marks_but_keep_their_lines():
    text = "- first\n* second\n+ third\n1. fourth\n2) fifth\n> quoted"
    assert plain_text(text) == "first\nsecond\nthird\nfourth\nfifth\nquoted"


def test_bold_after_a_bullet_is_cleaned_too():
    assert plain_text("* **Apple** rose") == "Apple rose"
    assert plain_text("**Bold intro**: details") == "Bold intro: details"


def test_backticks_and_strike_through_marks_are_removed():
    assert plain_text("Use `get_news` for ~~old~~ news") == "Use get_news for old news"


def test_an_underscore_inside_a_word_stays():
    assert plain_text("the snake_case name and _emphasis_") == "the snake_case name and emphasis"


def test_a_table_becomes_plain_rows_without_the_separator():
    table = "| Topic | Tone |\n|---|---|\n| Earnings | Neutral |"
    assert plain_text(table) == "Topic Tone\nEarnings Neutral"
    assert plain_text("| a | b |\n| :---: | ---: |\n| 1 | 2 |") == "a b\n1 2"


def test_horizontal_rules_and_blank_lines_are_dropped():
    assert plain_text("one\n\n---\n\n\ntwo\n***\n") == "one\ntwo"


def test_spaces_inside_a_line_are_collapsed():
    assert plain_text("a    b \t c") == "a b c"


def test_plain_text_of_nothing_is_empty():
    for value in (None, "", "   \n\n", [], [{"type": "image"}]):
        assert plain_text(value) == ""


def test_plain_text_reads_a_reply_given_as_content_parts():
    assert plain_text([{"type": "text", "text": "## Big news\n\nShares rose."}]) == "Big news\nShares rose."


def test_numbers_and_signs_inside_a_line_are_kept():
    assert plain_text("Shares fell -5% to $10.50 (1.2x volume)") == "Shares fell -5% to $10.50 (1.2x volume)"


def test_a_single_tilde_for_about_is_kept_but_strike_through_goes():
    assert plain_text("Target ~$50, a ~5% move, ~~old~~ news") == "Target ~$50, a ~5% move, old news"


def test_a_year_at_the_start_of_a_line_is_not_a_list_number():
    assert plain_text("2024. Shares rose.\n12. twelfth item\n3) third") == \
        "2024. Shares rose.\ntwelfth item\nthird"


def test_a_greater_than_sign_without_a_space_is_not_a_quote_mark():
    assert plain_text(">$100 target\n> quoted") == ">$100 target\nquoted"


def test_a_line_break_inside_a_table_cell_becomes_a_space():
    table = "| Topic | Detail |\n|---|---|\n| A | first<br>second<br/>third |"
    assert plain_text(table) == "Topic Detail\nA first second third"


def test_an_image_keeps_its_description():
    assert plain_text("![Price chart](https://example.com/c.png) and more") == "Price chart and more"


def test_a_link_address_with_brackets_is_removed_whole():
    assert plain_text("See [Foo](https://en.wikipedia.org/wiki/Foo_(bar)) now") == "See Foo now"


# ---- shorten ----

def test_a_short_text_is_unchanged():
    assert shorten("hello world", 700) == "hello world"
    assert shorten("", 700) == ""


def test_a_text_of_exactly_the_limit_is_unchanged():
    text = "x" * 700
    assert shorten(text, 700) == text


def test_a_longer_text_is_cut_to_at_most_the_limit_and_ends_with_dots():
    result = shorten("word " * 400, 700)
    assert len(result) <= 700
    assert result.endswith(ELLIPSIS)
    assert result == ("word " * 400)[:len(result) - 3].rstrip() + "..."


def test_the_cut_never_splits_a_word():
    text = " ".join(f"word{i:03d}" for i in range(200))  # 7-character words
    result = shorten(text, 100)
    assert len(result) <= 100
    kept = result[:-len(ELLIPSIS)].split(" ")
    assert all(word in text.split(" ") for word in kept)  # every kept word is whole


def test_a_cut_that_lands_exactly_after_a_word_keeps_that_word():
    # limit 8 leaves room for 5 characters: exactly "hello", and a space follows it
    assert shorten("hello world again", 8) == "hello..."


def test_a_cut_inside_a_word_backs_up_to_the_word_before():
    # limit 11 leaves room for 8 characters: "hello wo", which cuts "world" in half
    assert shorten("hello world again", 11) == "hello..."


def test_one_very_long_word_is_cut_at_the_limit():
    result = shorten("x" * 1000, 700)
    assert result == "x" * 697 + "..."
    assert len(result) == 700


def test_a_cut_can_fall_on_a_line_break():
    # limit 20 leaves room for 17 characters: "first line here\ns", which cuts "second" in half
    assert shorten("first line here\nsecond line here", 20) == "first line here..."


def test_trailing_punctuation_before_the_dots_is_dropped():
    # limit 15 leaves room for 12 characters: "alpha, beta," and a space follows it
    assert shorten("alpha, beta, gamma delta", 15) == "alpha, beta..."


def test_no_fourth_dot_after_a_full_stop():
    # limit 15 leaves room for 12 characters: "Shares rose." and a space follows it
    assert shorten("Shares rose. Costs fell.", 15) == "Shares rose..."


def test_no_dangling_bracket_before_the_dots():
    # limit 12 leaves room for 9 characters: "revenue (" and a space follows it
    assert shorten("revenue ( about five", 12) == "revenue..."


def test_a_text_of_701_characters_with_spaces_is_cut_to_at_most_700():
    text = ("abcd " * 141)[:701]
    result = shorten(text, 700)
    assert len(result) <= 700
    assert result.endswith("...")
    assert all(word == "abcd" for word in result[:-3].split(" "))


def test_a_limit_with_no_room_for_the_dots_is_a_plain_cut():
    assert shorten("hello world", 3) == "hel"
    assert shorten("hello world", 0) == ""
    assert shorten("hello world", -1) == ""
    assert shorten("hi", 3) == "hi"
