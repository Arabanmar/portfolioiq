"""Small text helpers (no AI): reading a model reply as text, markdown to plain text, cutting to a length."""

from __future__ import annotations

import re

ELLIPSIS = "..."

# Lines that carry no words: blank lines, horizontal rules and the "|---|---|" row of a markdown table.
_SEPARATOR_LINE = re.compile(r"^[\s|:\-]*$")
# A heading, quote, bullet or number at the start of a line. A number mark is 1-2
# digits, so a line that starts with a year ("2024. Shares rose") keeps it, and a
# quote mark needs a space after it, so ">$100 target" keeps its sign.
_LINE_MARK = re.compile(r"^[ \t]*(?:#{1,6}[ \t]+|>[ \t]+|[-*+][ \t]+|\d{1,2}[.)][ \t]+)")
# A link or an image. Its address may hold one level of brackets, as a Wikipedia address ending in (bar) does.
_LINK = re.compile(r"!?\[([^\]]*)\]\((?:[^()]|\([^()]*\))*\)")
_BREAK = re.compile(r"<br\s*/?>", re.IGNORECASE)
# Bold, italics and code marks, and "~~" strike-through. A single "~" ("~$50" for "about
# $50") stays, and so does an underscore inside a word (snake_case).
_EMPHASIS = re.compile(r"[`*]+|~~+|(?<!\w)_+|_+(?!\w)")


def as_text(value) -> str:
    """Plain text from a model reply: a string, or (for some providers) a list of content parts."""
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (list, tuple)):
        return "\n".join(as_text(part.get("text") if isinstance(part, dict) else part)
                         for part in value)
    return str(value)


def plain_text(markdown) -> str:
    """A markdown report as plain text: one line per paragraph, bullet or table row.

    Heading, quote, bullet and number marks, bold and italics, backticks and
    link targets are removed ("[Reuters](https://...)" keeps "Reuters"), table
    separator rows and horizontal rules are dropped, and blank lines go.
    """
    lines = []
    for raw in as_text(markdown).splitlines():
        raw = _BREAK.sub(" ", raw)  # a line break inside a table cell
        if _SEPARATOR_LINE.match(raw):
            continue
        line = _LINE_MARK.sub("", raw)
        line = _LINK.sub(r"\1", line)
        line = _EMPHASIS.sub("", line)
        line = " ".join(line.replace("|", " ").split())
        if line:
            lines.append(line)
    return "\n".join(lines)


def shorten(text: str, limit: int) -> str:
    """At most `limit` characters. A longer text is cut at a whole word and ends with "..."."""
    if len(text) <= limit:
        return text
    if limit <= len(ELLIPSIS):  # no room for a word and the dots
        return text[:max(limit, 0)]
    cut = text[: limit - len(ELLIPSIS)]
    if not text[len(cut)].isspace():  # the cut landed inside a word: back up to the word before
        boundary = max(cut.rfind(" "), cut.rfind("\n"))
        if boundary > 0:
            cut = cut[:boundary]
    # No "demand...." after a full stop, and no dangling "(" or "$" before the dots.
    return cut.rstrip(" \n,;:-.($&") + ELLIPSIS
