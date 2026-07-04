#!/usr/bin/env python3
"""classify.py - Backfill music language genre tags in Jellyfin using SearXNG.

The Android client (jellywing/gramophone) classifies songs by language from
metadata heuristics. When those heuristics fall through to "English" for
romanized Hindi titles ("Teri ni kararan") or unaccented Spanish ("No Me
Conoce", "KAROL G - PROVENZA"), this script queries SearXNG to look up the
language and writes a "Language: <X>" genre tag onto the Jellyfin item so
the client can pick it up.

Required environment variables (no defaults -- script refuses to run if any
are missing, so nothing is silently leaked from a partial config):

    SEARXNG_URL       Base URL of the SearXNG instance (no trailing slash).
    JELLYFIN_URL      Base URL of the Jellyfin server (no trailing slash).
    JELLYFIN_API_KEY  API key from Jellyfin Dashboard > Administration > API Keys.

Optional:

    DRY_RUN           If "1", do everything except the Jellyfin POST update.
    LIMIT             Maximum number of items to process (default: 200).
    LANGUAGE_HINTS_FILE  Path to a JSON file with {artist_keyword: language}
                        overrides (rare artists known to sing in a specific
                        language; loaded before SearXNG).
"""

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from html import unescape
from urllib.error import HTTPError, URLError

USER_AGENT = "jellywing-classify/1.0 (+https://github.com/birdrock00/jellywing)"

LANGUAGE_GENRE_PREFIX = "Language: "

LANGUAGE_NAMES = {
    "en": "English",
    "es": "Spanish",
    "hi": "Hindi",
    "pa": "Punjabi",
    "ta": "Tamil",
    "te": "Telugu",
    "bn": "Bengali",
    "mr": "Marathi",
    "gu": "Gujarati",
    "kn": "Kannada",
    "ml": "Malayalam",
    "ur": "Urdu",
    "ja": "Japanese",
    "ko": "Korean",
    "zh": "Chinese",
    "fr": "French",
    "de": "German",
    "it": "Italian",
    "pt": "Portuguese",
    "nl": "Dutch",
    "sv": "Swedish",
    "pl": "Polish",
    "ru": "Russian",
    "tr": "Turkish",
    "ar": "Arabic",
    "id": "Indonesian",
    "vi": "Vietnamese",
    "th": "Thai",
}

URL_DOMAIN_SIGNALS = [
    # (substring, language, weight)
    ("letras.com", "es", 4),
    ("letraseningles.com", "es", 3),
    ("lyricstranslate.com", None, 1),  # multilingual, look at content
    ("bollywoodlyrics.com", "hi", 5),
    ("hindilyrics.com", "hi", 5),
    ("lyricsmint.com", "hi", 4),
    ("punjabilyrics.com", "pa", 5),
    ("tamillyrics.com", "ta", 5),
    ("telugulyrics.com", "te", 5),
    ("genius.com", None, 1),  # Genius defaults to English; English Translation suffix = foreign
    ("lingopie.com", None, 2),  # language-learning site -- content decides
    ("musixmatch.com", None, 1),
    ("wikidata.org", None, 1),
    ("wikipedia.org", None, 1),
    ("azlyrics.com", "en", 3),
]

TITLE_CONTENT_SIGNALS = [
    # (regex, language, weight) -- applied to title+content of each result
    (r"\bspanish\b", "es", 3),
    (r"\bespañol\b", "es", 3),
    (r"\bcolombian\b", "es", 2),
    (r"\bmexican\b", "es", 2),
    (r"\blatin\b", "es", 1),
    (r"\breggaeton\b", "es", 2),
    (r"\bletras?\b", "es", 2),
    (r"\bhindi\b", "hi", 4),
    (r"\bbollywood\b", "hi", 3),
    (r"\bpunjabi\b", "pa", 4),
    (r"\btamil\b", "ta", 4),
    (r"\btelugu\b", "te", 4),
    (r"\bkorean\b", "ko", 3),
    (r"\bjapanese\b", "ja", 3),
    (r"\bchinese\b", "zh", 3),
    (r"\bmandarin\b", "zh", 3),
    (r"\bcantonese\b", "zh", 2),
    (r"\bfrench\b", "fr", 3),
    (r"\ballemand\b", "de", 2),
    (r"\bgerman\b", "de", 3),
    (r"\bitalian\b", "it", 3),
    (r"\bportuguese\b", "pt", 3),
    (r"\brussian\b", "ru", 3),
    (r"\bthai\b", "th", 3),
    (r"\bindonesian\b", "id", 3),
    (r"\bvietnamese\b", "vi", 3),
    (r"\barabic\b", "ar", 3),
    (r"\benglish translation\b", None, 0),  # marker only -- see English demotion below
]

# When "english translation" / "english lyrics" appears in a genius.com result,
# that's a strong negative signal for English. Weight subtracted from "en".
ENGLISH_DEMOTE_REGEX = re.compile(
    r"(english translation|english lyrics|translated to english|in english)"
)


def require_env(name):
    value = os.environ.get(name, "").strip()
    if not value:
        sys.stderr.write(
            f"ERROR: environment variable {name} is required (and must not be "
            f"empty). Refusing to continue with a partial config.\n"
        )
        sys.exit(2)
    return value


def fetch_jellyfin_items(base_url, api_key):
    url = f"{base_url}/Items?Recursive=true&IncludeItemTypes=Audio&Fields=Genres,Artists&Limit=10000"
    req = urllib.request.Request(url)
    req.add_header("X-Emby-Token", api_key)
    req.add_header("Accept", "application/json")
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read())
    return data.get("Items", [])


def has_language_genre(item):
    return any(
        isinstance(g, str) and g.startswith(LANGUAGE_GENRE_PREFIX)
        for g in (item.get("Genres") or [])
    )


def update_jellyfin_genre(base_url, api_key, item, language_code):
    item_id = item["Id"]
    current_genres = list(item.get("Genres") or [])
    genre_name = f"{LANGUAGE_GENRE_PREFIX}{LANGUAGE_NAMES.get(language_code, language_code)}"
    if genre_name in current_genres:
        return False, "already set"
    current_genres.append(genre_name)

    # Jellyfin's POST /Items/{id} requires the full BaseItemDto payload, not a
    # partial update. GET the current item, replace Genres, POST it back.
    get_req = urllib.request.Request(f"{base_url}/Items/{item_id}")
    get_req.add_header("X-Emby-Token", api_key)
    get_req.add_header("Accept", "application/json")
    with urllib.request.urlopen(get_req, timeout=30) as resp:
        full_item = json.loads(resp.read())

    full_item["Genres"] = current_genres

    url = f"{base_url}/Items/{item_id}"
    payload = json.dumps(full_item).encode("utf-8")
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("X-Emby-Token", api_key)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    with urllib.request.urlopen(req, timeout=30) as resp:
        resp.read()
    return True, genre_name


def searxng_search(searxng_url, query):
    params = urllib.parse.urlencode({"q": query, "language": "en", "safesearch": "0"})
    url = f"{searxng_url}/search?{params}"
    req = urllib.request.Request(url)
    req.add_header("User-Agent", USER_AGENT)
    req.add_header("Accept", "text/html")
    with urllib.request.urlopen(req, timeout=30) as resp:
        html = resp.read().decode("utf-8", errors="replace")
    return parse_searxng_results(html)


def parse_searxng_results(html):
    articles = re.findall(r'<article class="result[^"]*"[^>]*>(.*?)</article>', html, re.DOTALL)
    results = []
    for a in articles:
        # SearXNG emits <a href="..." class="url_header" ...> -- attributes in any order
        url_anchor = re.search(r'<a\b[^>]*class="url_header"[^>]*>', a)
        url = ""
        if url_anchor:
            href_match = re.search(r'href="([^"]+)"', url_anchor.group(0))
            if href_match:
                url = unescape(href_match.group(1))
        title_match = re.search(r'<h3[^>]*>(.*?)</h3>', a, re.DOTALL)
        content_match = re.search(r'<p class="content">(.*?)</p>', a, re.DOTALL)
        title = _strip_tags(title_match.group(1)) if title_match else ""
        content = _strip_tags(content_match.group(1)) if content_match else ""
        if url:
            results.append({"url": url, "title": title, "content": content})
    return results


def _strip_tags(html):
    text = re.sub(r"<[^>]+>", " ", html)
    return re.sub(r"\s+", " ", unescape(text)).strip()


def classify_song(searxng_url, artist, title, hints=None):
    query = f"{artist} {title} song lyrics language".strip()
    if not query:
        return None, "empty query"

    if hints:
        for keyword, lang in hints.items():
            if keyword.lower() in artist.lower():
                return lang, f"hint match: {keyword}"

    try:
        results = searxng_search(searxng_url, query)
    except (HTTPError, URLError, TimeoutError) as exc:
        return None, f"searxng error: {exc}"

    if not results:
        return None, "no results"

    scores = {}
    for r in results[:12]:
        url_lc = r["url"].lower()
        title_lc = r["title"].lower()
        content_lc = r["content"].lower()
        combined = f"{title_lc} {content_lc}"

        for substr, lang, weight in URL_DOMAIN_SIGNALS:
            if substr in url_lc and lang:
                scores[lang] = scores.get(lang, 0) + weight

        for pattern, lang, weight in TITLE_CONTENT_SIGNALS:
            if re.search(pattern, combined):
                if lang:
                    scores[lang] = scores.get(lang, 0) + weight

        if "genius.com" in url_lc and ENGLISH_DEMOTE_REGEX.search(combined):
            scores["en"] = scores.get("en", 0) - 3

        if "lingopie.com" in url_lc:
            m = re.search(r"learn (\w+) with", title_lc)
            if m:
                target = m.group(1).lower()
                lang = _LANG_BY_NAME.get(target)
                if lang:
                    scores[lang] = scores.get(lang, 0) + 4

    if not scores:
        return None, "no language signal"

    best_lang = max(scores, key=lambda k: scores[k])
    best_score = scores[best_lang]

    if best_score < 3:
        return None, f"weak signal (top={best_lang}@{best_score})"

    if best_lang not in LANGUAGE_NAMES:
        return None, f"unknown language code {best_lang}"

    return best_lang, f"score={best_score}, scores={scores}"


_LANG_BY_NAME = {v.lower(): k for k, v in LANGUAGE_NAMES.items()}


def load_hints(path):
    if not path:
        return {}
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError) as exc:
        sys.stderr.write(f"WARN: failed to load hints file {path}: {exc}\n")
        return {}


def main():
    searxng_url = require_env("SEARXNG_URL")
    jellyfin_url = require_env("JELLYFIN_URL")
    jellyfin_api_key = require_env("JELLYFIN_API_KEY")

    dry_run = os.environ.get("DRY_RUN", "").strip() == "1"
    try:
        limit = int(os.environ.get("LIMIT", "200"))
    except ValueError:
        limit = 200
    hints = load_hints(os.environ.get("LANGUAGE_HINTS_FILE", "").strip())

    sys.stderr.write(f"Fetching items from Jellyfin ...\n")
    items = fetch_jellyfin_items(jellyfin_url, jellyfin_api_key)
    sys.stderr.write(f"Found {len(items)} audio items total\n")

    todo = [i for i in items if not has_language_genre(i)]
    sys.stderr.write(f"Items needing classification: {len(todo)}\n")
    if limit > 0:
        todo = todo[:limit]
        sys.stderr.write(f"Processing up to {limit}\n")

    updated = 0
    skipped = 0
    failed = 0
    for idx, item in enumerate(todo, 1):
        title = item.get("Name") or ""
        artists = item.get("Artists") or []
        artist = artists[0] if artists else ""
        label = f"{artist} - {title}".strip(" -") or item.get("Id", "?")
        lang_code, reason = classify_song(searxng_url, artist, title, hints)
        if not lang_code:
            sys.stderr.write(f"[{idx}/{len(todo)}] {label}  skip ({reason})\n")
            skipped += 1
            continue

        genre_name = f"{LANGUAGE_GENRE_PREFIX}{LANGUAGE_NAMES[lang_code]}"
        if dry_run:
            sys.stderr.write(f"[{idx}/{len(todo)}] {label}  -> {lang_code} (dry-run, {reason})\n")
            updated += 1
            continue

        try:
            ok, message = update_jellyfin_genre(jellyfin_url, jellyfin_api_key, item, lang_code)
        except (HTTPError, URLError) as exc:
            sys.stderr.write(f"[{idx}/{len(todo)}] {label}  error: {exc}\n")
            failed += 1
            continue

        if ok:
            sys.stderr.write(f"[{idx}/{len(todo)}] {label}  -> {genre_name} ({reason})\n")
            updated += 1
        else:
            sys.stderr.write(f"[{idx}/{len(todo)}] {label}  {message}\n")
            skipped += 1

        time.sleep(0.4)

    sys.stderr.write(
        f"\nDone. updated={updated} skipped={skipped} failed={failed} dry_run={dry_run}\n"
    )


if __name__ == "__main__":
    main()