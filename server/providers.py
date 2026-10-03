"""Hosted providers. Secrets never leave the gateway or enter error messages."""
import base64
import io
import json
import re
import wave
from urllib.error import HTTPError
from urllib.request import Request, urlopen


class ProviderError(ValueError):
    pass


def json_api(url, body=None, headers=None, timeout=20, limit=2_000_000):
    data = None if body is None else json.dumps(body).encode()
    request = Request(url, data, {"Content-Type": "application/json", **(headers or {})})
    try:
        with urlopen(request, timeout=timeout) as response:
            raw = response.read(limit+1)
    except HTTPError as exc:
        if exc.code == 429:
            raise ProviderError("Provider quota reached. Try again later or ask the admin to check billing.") from None
        if exc.code in (401, 403):
            raise ProviderError("Provider access was denied. Ask the admin to verify the API key and model access.") from None
        raise ProviderError("The provider could not complete this request (HTTP %s)." % exc.code) from None
    if len(raw) > limit:
        raise ProviderError("Provider response exceeded its size limit")
    return json.loads(raw)


def model_name(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_./-]{1,100}", value) or ".." in value:
        raise ValueError("Invalid provider model name")
    return value


def groq_models(key):
    value = json_api("https://api.groq.com/openai/v1/models", headers={"Authorization": "Bearer "+key})
    return [m["id"] for m in value.get("data", []) if isinstance(m.get("id"), str)]


def groq_stream(key, model, messages, control, emit, stream_function):
    """Use the shared cancellable SSE transport, with no automatic duplicate retry."""
    payload = {"model": model_name(model), "messages": messages, "stream": True,
               "temperature": .65, "max_completion_tokens": 768,
               "stream_options": {"include_usage": True}}
    answer, usage = "", {}
    for chunk in stream_function("https://api.groq.com/openai/v1/chat/completions", payload,
                                 control, 35, {"Authorization": "Bearer "+key}):
        control.check()
        if chunk.get("usage"):
            usage = chunk["usage"]
        for choice in chunk.get("choices", []):
            delta = choice.get("delta", {}).get("content") or ""
            if not isinstance(delta, str):
                raise ProviderError("Unexpected provider reply")
            answer += delta
            if len(answer) > 14000:
                raise ProviderError("Provider answer too long")
            if delta and emit:
                emit("token", {"text": delta})
    if not answer.strip():
        raise ProviderError("The model returned an empty answer")
    return answer, usage


def gemini_speech(key, model, voice, text):
    if not isinstance(text, str) or not 1 <= len(text.strip()) <= 900:
        raise ValueError("Speech needs 1–900 characters")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", voice):
        raise ValueError("Invalid speech voice")
    # Current Interactions API: exact transcript, explicit voice/style, no stored interaction.
    payload = {"model": model_name(model), "store": False,
               "input": [{"type": "user_input", "content": [{"type": "text", "text": text,
                         "annotations": [{"type": "speech_metadata", "style": "Warm, clear, conversational; a little dry wit. Natural pace."}]}]}],
               "response_format": {"type": "audio", "mime_type": "audio/wav"},
               "generation_config": {"speech_config": [{"voice": voice}]}}
    result = json_api("https://generativelanguage.googleapis.com/v1beta/interactions", payload,
                      {"x-goog-api-key": key}, timeout=18, limit=7_000_000)
    audio = result.get("output_audio")
    if not audio:
        # Raw REST puts audio in steps; output_audio is an SDK convenience field.
        candidates = [x for step in result.get("steps", []) if step.get("type") == "model_output"
                      for x in step.get("content", []) if x.get("type") == "audio"]
        audio = candidates[-1] if candidates else None
    if not isinstance(audio, dict) or not isinstance(audio.get("data"), str):
        raise ProviderError("Gemini returned no speech audio")
    raw = base64.b64decode(audio["data"], validate=True)
    if len(raw) > 5_000_000:
        raise ProviderError("Speech audio too large")
    if raw.startswith(b"RIFF") and raw[8:12] == b"WAVE":
        return raw
    if audio.get("mime_type", "").startswith("audio/l16"):
        out = io.BytesIO()
        with wave.open(out, "wb") as wav:
            wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(24000); wav.writeframes(raw)
        return out.getvalue()
    raise ProviderError("Gemini returned an unsupported audio format")
