import json
import re
import types
from pathlib import Path

import pytest

from piq_server import agents, cache, settings

DATE = "2026-11-09"
KEY = "key-1"
POSITIONS = [{"ticker": "AAPL", "quantity": 10, "average_price": 150.0}]


@pytest.fixture(autouse=True)
def real_mode(monkeypatch):
    """These tests describe the real-mode file names; the fake-mode ones set FAKE_AI themselves."""
    monkeypatch.setattr(settings, "FAKE_AI", False)


def result_for(ticker="AAPL", **changes):
    result = {
        "ticker": ticker,
        "rating": "Buy",
        "decision": "**Rating**: Buy",
        "reports": {"market": "m", "news": "n", "fundamentals": "f"},
        "confidencePercent": 90.0,
        "confidenceLevel": "High",
        "votes": {"researchManager": "Buy", "trader": "Buy", "final": "Buy"},
        "newsSummary": "News.",
        "sources": ["Yahoo Finance news, past 7 days"],
        "bullCase": "For.",
        "bearCase": "Against.",
    }
    result.update(changes)
    return result


def saved_file(tmp_path, ticker="AAPL", date=DATE, suffix=".json"):
    return tmp_path / "cache" / date / f"{ticker}{suffix}"


def rewrite(path, change):
    entry = json.loads(path.read_text(encoding="utf-8"))
    change(entry)
    path.write_text(json.dumps(entry), encoding="utf-8")


# ---- saving and loading ----

def test_a_saved_result_is_loaded_back(tmp_path):
    assert cache.save("AAPL", DATE, KEY, result_for()) is True
    assert saved_file(tmp_path).is_file()
    assert cache.load("AAPL", DATE, KEY) == result_for()


def test_the_file_records_what_it_was_made_for(tmp_path):
    cache.save("AAPL", DATE, KEY, result_for())
    entry = json.loads(saved_file(tmp_path).read_text(encoding="utf-8"))
    assert entry["version"] == cache.CACHE_VERSION
    assert entry["ticker"] == "AAPL"
    assert entry["tradeDate"] == DATE
    assert entry["runKey"] == KEY
    assert re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}", entry["savedAt"])
    assert entry["result"] == result_for()


def test_a_result_with_text_in_many_forms_survives_the_round_trip():
    result = result_for(reports={"market": [{"type": "text", "text": "m"}], "news": "n", "fundamentals": "é ü 日本"},
                        decision=[{"type": "text", "text": "**Rating**: Buy"}])
    cache.save("AAPL", DATE, KEY, result)
    assert cache.load("AAPL", DATE, KEY) == result


def test_there_is_one_file_per_stock_and_day_and_a_newer_save_replaces_it(tmp_path):
    cache.save("AAPL", DATE, "key-1", result_for(decision="first"))
    cache.save("AAPL", DATE, "key-2", result_for(decision="second"))

    assert [p.name for p in (tmp_path / "cache" / DATE).iterdir()] == ["AAPL.json"]
    assert cache.load("AAPL", DATE, "key-2")["decision"] == "second"
    assert cache.load("AAPL", DATE, "key-1") is None


def test_fake_results_are_saved_beside_real_ones_never_over_them(tmp_path, monkeypatch):
    cache.save("AAPL", DATE, "real-key", result_for(decision="real"))
    monkeypatch.setattr(settings, "FAKE_AI", True)
    cache.save("AAPL", DATE, "fake-key", result_for(decision="fake"))

    assert sorted(p.name for p in (tmp_path / "cache" / DATE).iterdir()) == ["AAPL.fake.json", "AAPL.json"]
    assert cache.load("AAPL", DATE, "fake-key")["decision"] == "fake"
    monkeypatch.setattr(settings, "FAKE_AI", False)
    assert cache.load("AAPL", DATE, "real-key")["decision"] == "real"


# ---- when a saved result is not reused ----

def test_nothing_saved_means_nothing_loaded():
    assert cache.load("AAPL", DATE, KEY) is None


def test_another_day_another_run_or_another_stock_is_a_miss(tmp_path):
    cache.save("AAPL", DATE, KEY, result_for())

    assert cache.load("AAPL", "2026-11-10", KEY) is None
    assert cache.load("AAPL", DATE, "other-key") is None
    assert cache.load("MSFT", DATE, KEY) is None


def test_a_file_copied_under_another_stocks_name_is_not_served(tmp_path):
    cache.save("AAPL", DATE, KEY, result_for())
    (tmp_path / "cache" / DATE / "MSFT.json").write_bytes(saved_file(tmp_path).read_bytes())

    assert cache.load("MSFT", DATE, KEY) is None


@pytest.mark.parametrize("content", ["", "{not json", "[]", "null", '"text"', "123", '{"version": 1}'])
def test_a_corrupt_or_odd_file_is_a_miss(tmp_path, content):
    path = saved_file(tmp_path)
    path.parent.mkdir(parents=True)
    path.write_text(content, encoding="utf-8")

    assert cache.load("AAPL", DATE, KEY) is None


def test_a_file_that_is_not_utf8_is_a_miss(tmp_path):
    path = saved_file(tmp_path)
    path.parent.mkdir(parents=True)
    path.write_bytes(b"\xff\xfe\x00not text")

    assert cache.load("AAPL", DATE, KEY) is None


def test_a_file_from_another_version_is_a_miss(tmp_path):
    cache.save("AAPL", DATE, KEY, result_for())
    rewrite(saved_file(tmp_path), lambda entry: entry.update(version=cache.CACHE_VERSION + 1))

    assert cache.load("AAPL", DATE, KEY) is None


@pytest.mark.parametrize("field", ["version", "ticker", "tradeDate", "runKey", "result"])
def test_a_file_missing_one_of_its_fields_is_a_miss(tmp_path, field):
    cache.save("AAPL", DATE, KEY, result_for())
    rewrite(saved_file(tmp_path), lambda entry: entry.pop(field))

    assert cache.load("AAPL", DATE, KEY) is None


@pytest.mark.parametrize("field", cache.REQUIRED_KEYS)
def test_a_result_saved_before_a_field_existed_is_a_miss(tmp_path, field):
    cache.save("AAPL", DATE, KEY, result_for())
    rewrite(saved_file(tmp_path), lambda entry: entry["result"].pop(field))

    assert cache.load("AAPL", DATE, KEY) is None


@pytest.mark.parametrize("field, value", [
    ("confidencePercent", "90"), ("confidencePercent", True), ("confidencePercent", None),
    ("confidenceLevel", 5), ("votes", "Buy"), ("votes", None),
    ("sources", "Yahoo"), ("sources", [1, 2]), ("sources", None),
    ("newsSummary", None), ("newsSummary", 5), ("bullCase", 5), ("bearCase", ["x"]),
    ("reports", "text"), ("reports", None),
    ("rating", "REVIEW"), ("rating", ""), ("decision", ""), ("decision", None),
])
def test_a_saved_result_with_a_wrong_type_or_an_incomplete_value_is_a_miss(tmp_path, field, value):
    cache.save("AAPL", DATE, KEY, result_for())
    rewrite(saved_file(tmp_path), lambda entry: entry["result"].update({field: value}))

    assert cache.load("AAPL", DATE, KEY) is None


def test_a_saved_result_with_an_empty_report_is_a_miss(tmp_path):
    cache.save("AAPL", DATE, KEY, result_for())
    rewrite(saved_file(tmp_path), lambda entry: entry["result"]["reports"].update(news=""))

    assert cache.load("AAPL", DATE, KEY) is None


def test_a_result_that_is_not_an_object_or_names_another_stock_is_a_miss(tmp_path):
    cache.save("AAPL", DATE, KEY, result_for())
    rewrite(saved_file(tmp_path), lambda entry: entry.update(result=["not", "an", "object"]))
    assert cache.load("AAPL", DATE, KEY) is None

    cache.save("AAPL", DATE, KEY, result_for())
    rewrite(saved_file(tmp_path), lambda entry: entry["result"].update(ticker="MSFT"))
    assert cache.load("AAPL", DATE, KEY) is None


def test_the_required_keys_are_what_analyze_stock_returns():
    assert set(cache.REQUIRED_KEYS) == set(agents._fake_analysis("AAPL"))


# ---- what is worth saving ----

@pytest.mark.parametrize("rating", ["REVIEW", "", None, "Maybe", 5])
def test_a_result_without_a_readable_rating_is_not_saved(tmp_path, rating):
    assert cache.save("AAPL", DATE, KEY, result_for(rating=rating)) is False
    assert not saved_file(tmp_path).exists()


@pytest.mark.parametrize("decision", ["", "   \n", None, [], [{"type": "text", "text": " "}]])
def test_a_result_without_a_decision_is_not_saved(tmp_path, decision):
    assert cache.save("AAPL", DATE, KEY, result_for(decision=decision)) is False
    assert not saved_file(tmp_path).exists()


@pytest.mark.parametrize("reports", [
    {"market": "", "news": "n", "fundamentals": "f"},
    {"market": "m", "news": "  \n", "fundamentals": "f"},
    {"market": "m", "news": "n"},  # a report that is missing
    {"market": "m", "news": "n", "fundamentals": []},
    {"market": "m", "news": "n", "fundamentals": [{"type": "text", "text": " "}]},
    {}, None, "text", ["list"],
])
def test_a_result_with_an_empty_report_from_an_analyst_that_is_on_is_not_saved(tmp_path, reports):
    assert cache.save("AAPL", DATE, KEY, result_for(reports=reports)) is False
    assert not saved_file(tmp_path).exists()


def test_an_empty_report_from_an_analyst_that_is_switched_off_does_not_matter(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "ANALYSTS", ["market"])
    reports = {"market": "m", "news": "", "fundamentals": ""}

    assert cache.save("AAPL", DATE, KEY, result_for(reports=reports)) is True
    assert cache.load("AAPL", DATE, KEY)["reports"] == reports


def test_what_counts_as_complete():
    assert cache.is_complete(result_for())
    assert cache.is_complete(result_for(rating=" sell "))
    assert cache.is_complete(result_for(decision=[{"type": "text", "text": "d"}]))
    assert not cache.is_complete({})
    assert not cache.is_complete(result_for(rating="REVIEW"))
    assert not cache.is_complete(result_for(reports={"market": "m", "news": "", "fundamentals": "f"}))


# ---- file names that could escape the folder ----

@pytest.mark.parametrize("ticker", ["../evil", "a/b", "aapl", "", "A" * 11, ".AAPL", "AAPL\\X",
                                    "AAPL/../X", "1ABC", "AAPL\n", "AAPL ", "ÀAPL"])
def test_an_unsafe_ticker_is_never_saved_or_loaded(tmp_path, ticker):
    assert cache.save(ticker, DATE, KEY, result_for(ticker=ticker)) is False
    assert cache.load(ticker, DATE, KEY) is None
    assert list(tmp_path.rglob("*")) == []
    assert list(tmp_path.parent.glob("evil*")) == []


@pytest.mark.parametrize("date", ["../x", "2026-11-9", "", "2026-11-09/../x", "today", "20261109",
                                  "2026-11-09\n", "٢٠٢٦-١١-٠٩"])
def test_an_unsafe_date_is_never_saved_or_loaded(tmp_path, date):
    assert cache.save("AAPL", date, KEY, result_for()) is False
    assert cache.load("AAPL", date, KEY) is None
    assert list(tmp_path.rglob("*")) == []


@pytest.mark.parametrize("ticker", ["CON", "PRN", "AUX", "NUL", "COM0", "COM1", "COM9", "LPT0", "LPT1", "LPT9", "CON.A"])
def test_a_ticker_that_is_a_windows_device_name_is_never_saved_or_loaded(tmp_path, ticker):
    assert cache.save(ticker, DATE, KEY, result_for(ticker=ticker)) is False
    assert cache.load(ticker, DATE, KEY) is None
    assert list(tmp_path.rglob("*")) == []


@pytest.mark.parametrize("ticker", ["CONA", "COM", "COM10", "LPT", "NULL", "AUXX", "BCON"])
def test_tickers_that_only_look_like_device_names_are_saved(ticker):
    assert cache.save(ticker, DATE, KEY, result_for(ticker=ticker)) is True
    assert cache.load(ticker, DATE, KEY)["ticker"] == ticker


def test_tickers_with_dots_and_dashes_are_fine(tmp_path):
    for ticker in ("BRK.B", "BF-B", "A"):
        assert cache.save(ticker, DATE, KEY, result_for(ticker=ticker)) is True
        assert cache.load(ticker, DATE, KEY)["ticker"] == ticker


# ---- saving never fails an analysis ----

def test_no_temporary_file_is_left_behind(tmp_path):
    cache.save("AAPL", DATE, KEY, result_for())
    assert [p.name for p in (tmp_path / "cache" / DATE).iterdir()] == ["AAPL.json"]


def test_a_disk_error_while_saving_is_logged_not_raised(tmp_path, monkeypatch, capsys):
    def failing_replace(source, destination):
        raise OSError("disk full")

    monkeypatch.setattr(cache, "os", types.SimpleNamespace(replace=failing_replace))

    assert cache.save("AAPL", DATE, KEY, result_for()) is False
    assert "Could not save the result for AAPL" in capsys.readouterr().out
    assert list((tmp_path / "cache" / DATE).iterdir()) == []  # no half-written file, no temporary file


def test_a_file_that_is_in_use_keeps_the_old_result(tmp_path, monkeypatch):
    cache.save("AAPL", DATE, KEY, result_for(decision="old"))

    def refused(source, destination):
        raise PermissionError("another process has the file open")

    with monkeypatch.context() as scoped:  # undoes only this change, not the isolation fixtures
        scoped.setattr(cache, "os", types.SimpleNamespace(replace=refused))
        assert cache.save("AAPL", DATE, KEY, result_for(decision="new")) is False

    assert cache.load("AAPL", DATE, KEY)["decision"] == "old"


def test_a_result_that_cannot_be_written_as_json_is_not_saved(tmp_path):
    assert cache.save("AAPL", DATE, KEY, result_for(reports={"market": object(), "news": "n", "fundamentals": "f"})) is False
    assert not saved_file(tmp_path).exists()
    assert not (tmp_path / "cache").exists() or list((tmp_path / "cache" / DATE).iterdir()) == []


def test_any_error_while_saving_is_swallowed(tmp_path, monkeypatch):
    def too_deep(*args, **kwargs):
        raise RecursionError("maximum recursion depth exceeded")

    monkeypatch.setattr(cache, "json", types.SimpleNamespace(dumps=too_deep, loads=json.loads))

    assert cache.save("AAPL", DATE, KEY, result_for()) is False
    assert not saved_file(tmp_path).exists()


def test_a_data_folder_that_cannot_be_created_is_not_an_error(tmp_path, monkeypatch):
    blocker = tmp_path / "blocker"
    blocker.write_text("a file, not a folder", encoding="utf-8")
    monkeypatch.setattr(settings, "DATA_DIR", blocker)

    assert cache.save("AAPL", DATE, KEY, result_for()) is False
    assert cache.load("AAPL", DATE, KEY) is None


def test_the_log_line_for_a_failed_save_is_plain_ascii(tmp_path, monkeypatch, capsys):
    blocker = tmp_path / "ملف"  # a path with non-ASCII characters, as on an Arabic Windows account
    blocker.write_text("a file, not a folder", encoding="utf-8")
    monkeypatch.setattr(settings, "DATA_DIR", blocker)

    assert cache.save("AAPL", DATE, KEY, result_for()) is False
    out = capsys.readouterr().out
    assert "Could not save the result for AAPL" in out
    assert out.isascii()


def test_a_log_line_that_cannot_be_printed_does_not_fail_the_save(tmp_path, monkeypatch):
    blocker = tmp_path / "blocker"
    blocker.write_text("a file, not a folder", encoding="utf-8")
    monkeypatch.setattr(settings, "DATA_DIR", blocker)

    def unprintable(*args, **kwargs):
        raise UnicodeEncodeError("ascii", "x", 0, 1, "cannot encode")

    monkeypatch.setattr("builtins.print", unprintable)

    assert cache.save("AAPL", DATE, KEY, result_for()) is False  # no exception


# ---- the run key ----

def test_the_run_key_is_the_same_for_the_same_holdings_in_any_order():
    book = [{"ticker": "AAPL", "quantity": 10, "average_price": 150.0},
            {"ticker": "MSFT", "quantity": 2, "average_price": 300.0}]
    assert cache.run_key(book) == cache.run_key(list(reversed(book)))
    assert cache.run_key(book) == cache.run_key([dict(p) for p in book])
    assert re.fullmatch(r"[0-9a-f]{16}", cache.run_key(book))


def test_the_run_key_changes_when_the_holdings_change():
    base = cache.run_key(POSITIONS)
    changed = [
        [{"ticker": "AAPL", "quantity": 11, "average_price": 150.0}],
        [{"ticker": "AAPL", "quantity": 10, "average_price": 151.0}],
        [{"ticker": "AAPL", "quantity": 10, "average_price": None}],
        [{"ticker": "MSFT", "quantity": 10, "average_price": 150.0}],
        POSITIONS + [{"ticker": "MSFT", "quantity": 1, "average_price": 300.0}],
        [],
    ]
    keys = {cache.run_key(book) for book in changed}
    assert base not in keys
    assert len(keys) == len(changed)


def test_a_rounding_wobble_in_a_holding_is_not_a_change():
    wobble = [{"ticker": "AAPL", "quantity": 10.000000000000002, "average_price": 150.00000000000003}]
    assert cache.run_key(wobble) == cache.run_key(POSITIONS)
    real_change = [{"ticker": "AAPL", "quantity": 10.001, "average_price": 150.0}]
    assert cache.run_key(real_change) != cache.run_key(POSITIONS)


def test_the_run_key_changes_with_the_settings_of_a_run(monkeypatch):
    keys = {cache.run_key(POSITIONS)}
    for name, value in (("FAKE_AI", True), ("ANALYSTS", ["market"]),
                        ("QUICK_MODEL", "another-quick-model"), ("DEEP_MODEL", "another-deep-model")):
        with monkeypatch.context() as scoped:  # undoes only this change, not the isolation fixtures
            scoped.setattr(settings, name, value)
            keys.add(cache.run_key(POSITIONS))

    assert len(keys) == 5
    assert settings.DATA_DIR != Path(settings.SERVER_DIR) / "data"  # the isolation fixture is still on


def test_the_order_of_the_analysts_does_not_change_the_run_key(monkeypatch):
    monkeypatch.setattr(settings, "ANALYSTS", ["market", "news"])
    one = cache.run_key(POSITIONS)
    monkeypatch.setattr(settings, "ANALYSTS", ["news", "market"])
    assert cache.run_key(POSITIONS) == one


# ---- the code stamp ----

def test_the_code_stamp_follows_the_code_that_derives_the_saved_fields(tmp_path):
    for name in cache._CODE_FILES:
        (tmp_path / name).write_text("version 1", encoding="utf-8")
    first = cache.code_stamp(tmp_path)

    assert re.fullmatch(r"[0-9a-f]{12}", first)
    assert cache.code_stamp(tmp_path) == first
    (tmp_path / "debate.py").write_text("version 2", encoding="utf-8")
    assert cache.code_stamp(tmp_path) != first


def test_each_of_the_files_changes_the_code_stamp(tmp_path):
    for name in cache._CODE_FILES:
        (tmp_path / name).write_text("version 1", encoding="utf-8")
    stamps = {cache.code_stamp(tmp_path)}
    for name in cache._CODE_FILES:
        (tmp_path / name).write_text("version 2", encoding="utf-8")
        stamps.add(cache.code_stamp(tmp_path))
        (tmp_path / name).write_text("version 1", encoding="utf-8")

    assert len(stamps) == len(cache._CODE_FILES) + 1


def test_a_missing_code_file_does_not_stop_the_server(tmp_path):
    empty = cache.code_stamp(tmp_path)  # none of the files exist
    assert re.fullmatch(r"[0-9a-f]{12}", empty)
    (tmp_path / "agents.py").write_text("x", encoding="utf-8")
    assert cache.code_stamp(tmp_path) != empty


def test_every_file_the_stamp_covers_exists():
    folder = Path(cache.__file__).parent
    for name in cache._CODE_FILES:
        assert (folder / name).is_file()


def test_the_run_key_changes_with_the_code_stamp(monkeypatch):
    before = cache.run_key(POSITIONS)
    monkeypatch.setattr(cache, "CODE_STAMP", "another-stamp")
    assert cache.run_key(POSITIONS) != before


def test_a_result_saved_by_older_code_is_not_served(monkeypatch):
    old_key = cache.run_key(POSITIONS)
    cache.save("AAPL", DATE, old_key, result_for())

    monkeypatch.setattr(cache, "CODE_STAMP", "the code was edited")
    assert cache.load("AAPL", DATE, cache.run_key(POSITIONS)) is None
