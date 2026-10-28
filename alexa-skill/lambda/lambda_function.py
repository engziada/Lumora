"""Lumora Alexa skill: "Alexa, ask Lumora assistant ..." answered by an OpenRouter model.

Runs as an Alexa-hosted (Python) skill. Standard library only, plus boto3 from the Lambda runtime
for reading the key from the skill's own S3 bucket. The OpenRouter key is never logged.
"""

import json
import logging
import os
import re
import socket
import urllib.error
import urllib.request

LOG = logging.getLogger()
LOG.setLevel(logging.INFO)

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "openrouter/free"
# Alexa-hosted skills: upload the key as a one-line text file to this object in the skill's bucket.
KEY_OBJECT = "Media/lumora/openrouter-key.txt"
# Alexa waits 8 s for the skill; leave room for Lambda start-up and the response itself.
HTTP_TIMEOUT_S = 6.5
MAX_HISTORY_TURNS = 6
MAX_TURN_CHARS = 600
MAX_SPEECH_CHARS = 700
MAX_TOKENS = 300

# The carrier word of each question intent is part of the question but not of the slot value.
CARRIERS = {
    "WhatIntent": "what",
    "WhoIntent": "who",
    "WhereIntent": "where",
    "WhenIntent": "when",
    "WhyIntent": "why",
    "HowIntent": "how",
    "WhichIntent": "which",
    "CanIntent": "can",
    "IsIntent": "is",
    "AskIntent": "",
}

SYSTEM_PROMPT = (
    "You are Lumora, a voice assistant on the user's Fire TV, speaking through Alexa. "
    "Answer in at most three short sentences that sound natural when read aloud. "
    "No markdown, lists, emoji, URLs or code. Reply in the language of the question. "
    "If you are not sure, say so briefly."
)

_KEY_CACHE = {"value": None}


class SkillError(Exception):
    """A failure with a message that is safe to speak to the user."""

    def __init__(self, speech, status=0):
        super().__init__(speech)
        self.speech = speech
        self.status = status


# ------------------------------------------------------------------ key


def load_key():
    """OpenRouter key from OPENROUTER_API_KEY (self-hosted) or the Alexa-hosted S3 bucket."""
    if _KEY_CACHE["value"]:
        return _KEY_CACHE["value"]
    key = os.environ.get("OPENROUTER_API_KEY", "").strip()
    bucket = os.environ.get("S3_PERSISTENCE_BUCKET", "")
    if not key and bucket:
        try:
            import boto3  # Provided by the AWS Lambda Python runtime.

            body = boto3.client("s3").get_object(Bucket=bucket, Key=KEY_OBJECT)["Body"].read()
            key = body.decode("utf-8").strip()
        except Exception as error:  # noqa: BLE001 - any S3 failure means "not configured".
            LOG.warning("OpenRouter key object not readable: %s", type(error).__name__)
            key = ""
    if not key:
        raise SkillError(
            "The OpenRouter key is not set up yet. Upload it to the skill's media storage "
            "as described in the Lumora guide, then try again."
        )
    _KEY_CACHE["value"] = key
    return key


# ------------------------------------------------------------- openrouter


def post_json(url, payload, headers, timeout):
    """POST JSON and return (status, parsed body). Separate so tests can replace it."""
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST"
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        try:
            body = json.loads(error.read().decode("utf-8"))
        except (ValueError, OSError):
            body = {}
        return error.code, body


def ask_model(key, model, messages):
    headers = {
        "Authorization": "Bearer " + key,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://github.com/engziada/Lumora",
        "X-Title": "Lumora Alexa skill",
    }
    payload = {"model": model, "messages": messages, "max_tokens": MAX_TOKENS}
    try:
        status, body = post_json(OPENROUTER_URL, payload, headers, HTTP_TIMEOUT_S)
    except (socket.timeout, TimeoutError):
        raise SkillError("The model took too long to answer. Please ask again.")
    except urllib.error.URLError as error:
        if isinstance(error.reason, (socket.timeout, TimeoutError)):
            raise SkillError("The model took too long to answer. Please ask again.")
        raise SkillError("I could not reach OpenRouter right now. Please try again.")
    except (OSError, ValueError):
        raise SkillError("I could not reach OpenRouter right now. Please try again.")
    if status == 401:
        raise SkillError("OpenRouter rejected the key. Check the key in the skill's media storage.", status)
    if status == 402:
        raise SkillError("OpenRouter reports a negative credit balance, so free models are paused.", status)
    if status == 429:
        raise SkillError("The free model limit is reached for now. Try again later.", status)
    if status != 200:
        LOG.warning("OpenRouter status %s", status)
        raise SkillError("OpenRouter could not answer right now. Please try again.", status)
    try:
        text = body["choices"][0]["message"]["content"] or ""
    except (KeyError, IndexError, TypeError):
        text = ""
    text = clean_for_speech(text)
    if not text:
        raise SkillError("The model sent an empty answer. Please ask again.")
    return text


# ---------------------------------------------------------------- text


_MARKDOWN = re.compile(r"[*_#`>|~]+")
_LINKS = re.compile(r"\[([^\]]*)\]\([^)]*\)")
_OPEN_TAGS = re.compile(r"\[\[open:[^\]]*\]\]")
_SPACES = re.compile(r"\s+")


def clean_for_speech(text):
    text = _OPEN_TAGS.sub("", text or "")
    text = _LINKS.sub(r"\1", text)
    text = _MARKDOWN.sub("", text)
    text = _SPACES.sub(" ", text).strip()
    if len(text) > MAX_SPEECH_CHARS:
        cut = text[:MAX_SPEECH_CHARS]
        end = max(cut.rfind(". "), cut.rfind("? "), cut.rfind("! "))
        text = cut[: end + 1] if end > MAX_SPEECH_CHARS // 2 else cut.rstrip() + "…"
    return text


def question_from(intent):
    name = intent.get("name", "")
    slot = (intent.get("slots") or {}).get("query") or {}
    value = (slot.get("value") or "").strip()
    if not value:
        return ""
    carrier = CARRIERS.get(name, "")
    return (carrier + " " + value).strip() if carrier else value


def trimmed_history(history):
    turns = [t for t in (history or []) if isinstance(t, dict) and t.get("role") in ("user", "assistant")]
    turns = turns[-MAX_HISTORY_TURNS:]
    return [{"role": t["role"], "content": str(t.get("content", ""))[:MAX_TURN_CHARS]} for t in turns]


# ------------------------------------------------------------- responses


def respond(speech, end_session, attributes=None, reprompt=None, card=True):
    response = {
        "outputSpeech": {"type": "PlainText", "text": speech},
        "shouldEndSession": end_session,
    }
    if card:
        response["card"] = {"type": "Simple", "title": "Lumora", "content": speech}
    if reprompt and not end_session:
        response["reprompt"] = {"outputSpeech": {"type": "PlainText", "text": reprompt}}
    return {"version": "1.0", "sessionAttributes": attributes or {}, "response": response}


def handle_question(question, attributes, locale):
    history = trimmed_history(attributes.get("history"))
    model = os.environ.get("OPENROUTER_MODEL", "").strip() or DEFAULT_MODEL
    system = SYSTEM_PROMPT + " The user's Alexa locale is " + (locale or "en-US") + "."
    messages = [{"role": "system", "content": system}] + history + [{"role": "user", "content": question}]
    try:
        answer = ask_model(load_key(), model, messages)
    except SkillError as error:
        return respond(error.speech, False, {"history": history}, "You can ask me something else.")
    history = trimmed_history(history + [{"role": "user", "content": question},
                                         {"role": "assistant", "content": answer}])
    return respond(answer, False, {"history": history}, "Anything else?")


def lambda_handler(event, context):  # noqa: ARG001 - Lambda signature.
    request = event.get("request", {})
    kind = request.get("type", "")
    locale = request.get("locale", "en-US")
    attributes = (event.get("session") or {}).get("attributes") or {}

    if kind == "LaunchRequest":
        return respond("Lumora here. What would you like to know?", False, attributes,
                       "Ask me anything, for example: what should I watch tonight?")
    if kind == "SessionEndedRequest":
        return {"version": "1.0", "response": {}}
    if kind != "IntentRequest":
        return respond("Sorry, I did not get that.", True, card=False)

    intent = request.get("intent", {})
    name = intent.get("name", "")
    if name in ("AMAZON.StopIntent", "AMAZON.CancelIntent", "AMAZON.NavigateHomeIntent"):
        return respond("Goodbye.", True, card=False)
    if name == "AMAZON.HelpIntent":
        return respond("Ask a question, starting with what, who, how, why, or tell me. "
                       "For example: what is a good comedy for tonight?", False, attributes,
                       "What would you like to know?", card=False)
    if name == "AMAZON.FallbackIntent":
        return respond("Try starting with what, who, how, or tell me.", False, attributes,
                       "What would you like to know?", card=False)
    if name in CARRIERS:
        question = question_from(intent)
        if not question:
            return respond("What would you like to know?", False, attributes, "Go ahead.", card=False)
        return handle_question(question, attributes, locale)
    return respond("Sorry, I can't help with that one.", False, attributes, "Anything else?", card=False)
