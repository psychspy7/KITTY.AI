"""Explicit web research; secrets stay on the brain, content never runs tools."""
import html
import json
import os
import re
import time
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen


def research(query):
    key = os.environ.get("BRAVE_SEARCH_API_KEY", "").strip()
    if not key:
        return {"mode": "web_unavailable", "actions": [], "reply": "Sir, web research isn't configured yet. Add BRAVE_SEARCH_API_KEY to data/secrets.env on the brain and restart it. ‘Search for …’ can still open your phone's browser."}
    query = query.strip()
    if not query or len(query) > 500:
        raise ValueError("Use a web research question of 1–500 characters")
    req = Request("https://api.search.brave.com/res/v1/web/search?" + urlencode({"q": query, "count": 3, "safesearch": "off"}), headers={"Accept": "application/json", "X-Subscription-Token": key, "User-Agent": "KITTY/0.3"})
    try:
        with urlopen(req, timeout=10) as response:
            raw = response.read(1_000_001)
        if len(raw) > 1_000_000:
            raise ValueError("Search response too large")
        items = json.loads(raw).get("web", {}).get("results", [])
        sources, sections = [], []
        clean = lambda value: html.unescape(re.sub(r"<[^>]*>", "", str(value)))[:600]
        for item in items[:3]:
            url = item.get("url", "")
            parsed = urlparse(url)
            if parsed.scheme != "https" or not parsed.hostname or parsed.username:
                continue
            title, excerpt = clean(item.get("title", "Source")), clean(item.get("description", ""))
            sources.append({"title": title, "url": url})
            sections.append(f"{len(sources)}. {title}\n{excerpt}\n{url}")
        reply = "Sir, here are live web search excerpts (open the sources to verify details):\n\n" + "\n\n".join(sections) if sections else "Sir, the search returned no usable results. Try a more specific question."
        return {"mode": "web", "reply": reply, "actions": [], "sources": sources, "searched_at": int(time.time())}
    except Exception:
        return {"mode": "web_unavailable", "actions": [], "reply": "Sir, web search is unavailable. Check the brain's internet, search API key and quota. I haven't verified an answer."}
