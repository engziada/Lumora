import os
import socket
import sys
import unittest
import urllib.error
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lambda"))

import lambda_function as skill  # noqa: E402


def intent_event(name, query=None, attributes=None, locale="en-US"):
    slots = {"query": {"name": "query", "value": query}} if query is not None else {}
    return {
        "session": {"attributes": attributes or {}},
        "request": {"type": "IntentRequest", "locale": locale, "intent": {"name": name, "slots": slots}},
    }


def ok_reply(text):
    return 200, {"choices": [{"message": {"content": text}}]}


class SkillTest(unittest.TestCase):
    def setUp(self):
        skill._KEY_CACHE["value"] = None
        patcher = mock.patch.dict(os.environ, {"OPENROUTER_API_KEY": "sk-or-test"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        os.environ.pop("OPENROUTER_MODEL", None)

    def speech(self, result):
        return result["response"]["outputSpeech"]["text"]

    def test_launch_keeps_session_open(self):
        result = skill.lambda_handler({"request": {"type": "LaunchRequest"}}, None)
        self.assertFalse(result["response"]["shouldEndSession"])
        self.assertIn("reprompt", result["response"])

    def test_question_rebuilds_carrier_and_answers(self):
        with mock.patch.object(skill, "post_json", return_value=ok_reply("Cairo is the capital.")) as post:
            result = skill.lambda_handler(intent_event("WhatIntent", "is the capital of egypt"), None)
        self.assertEqual("Cairo is the capital.", self.speech(result))
        payload = post.call_args[0][1]
        self.assertEqual("what is the capital of egypt", payload["messages"][-1]["content"])
        self.assertEqual("openrouter/free", payload["model"])
        self.assertEqual("Bearer sk-or-test", post.call_args[0][2]["Authorization"])
        history = result["sessionAttributes"]["history"]
        self.assertEqual(["user", "assistant"], [t["role"] for t in history])
        self.assertEqual("Lumora", result["response"]["card"]["title"])

    def test_ask_intent_has_no_carrier(self):
        with mock.patch.object(skill, "post_json", return_value=ok_reply("Sure.")) as post:
            skill.lambda_handler(intent_event("AskIntent", "a joke about cats"), None)
        self.assertEqual("a joke about cats", post.call_args[0][1]["messages"][-1]["content"])

    def test_history_is_sent_and_capped(self):
        old = [{"role": "user" if i % 2 == 0 else "assistant", "content": "x" * 900} for i in range(10)]
        with mock.patch.object(skill, "post_json", return_value=ok_reply("Fine.")) as post:
            result = skill.lambda_handler(intent_event("HowIntent", "are you", {"history": old}), None)
        messages = post.call_args[0][1]["messages"]
        self.assertEqual("system", messages[0]["role"])
        self.assertEqual(skill.MAX_HISTORY_TURNS + 2, len(messages))
        self.assertTrue(all(len(m["content"]) <= skill.MAX_TURN_CHARS for m in messages[1:-1]))
        self.assertEqual(skill.MAX_HISTORY_TURNS, len(result["sessionAttributes"]["history"]))

    def test_model_override(self):
        with mock.patch.dict(os.environ, {"OPENROUTER_MODEL": "google/gemma:free"}):
            with mock.patch.object(skill, "post_json", return_value=ok_reply("Hi.")) as post:
                skill.lambda_handler(intent_event("WhoIntent", "are you"), None)
        self.assertEqual("google/gemma:free", post.call_args[0][1]["model"])

    def test_markdown_and_open_tags_removed(self):
        reply = "**Bold** answer with [a link](https://x.y) and [[open:com.netflix.ninja]] `code`."
        with mock.patch.object(skill, "post_json", return_value=ok_reply(reply)):
            result = skill.lambda_handler(intent_event("WhatIntent", "is it"), None)
        self.assertEqual("Bold answer with a link and code.", self.speech(result))

    def test_long_answer_trimmed_at_sentence(self):
        reply = ("This sentence is long enough to count. " * 40).strip()
        with mock.patch.object(skill, "post_json", return_value=ok_reply(reply)):
            result = skill.lambda_handler(intent_event("WhatIntent", "is it"), None)
        text = self.speech(result)
        self.assertLessEqual(len(text), skill.MAX_SPEECH_CHARS)
        self.assertTrue(text.endswith("."))

    def test_status_errors_are_spoken(self):
        cases = {401: "rejected the key", 402: "credit", 429: "limit", 500: "could not answer"}
        for status, phrase in cases.items():
            with mock.patch.object(skill, "post_json", return_value=(status, {})):
                result = skill.lambda_handler(intent_event("WhatIntent", "is it"), None)
            self.assertIn(phrase, self.speech(result))
            self.assertFalse(result["response"]["shouldEndSession"])

    def test_timeouts(self):
        for error in (socket.timeout("t"), urllib.error.URLError(socket.timeout("t"))):
            with mock.patch.object(skill, "post_json", side_effect=error):
                result = skill.lambda_handler(intent_event("WhatIntent", "is it"), None)
            self.assertIn("too long", self.speech(result))

    def test_network_error(self):
        with mock.patch.object(skill, "post_json", side_effect=urllib.error.URLError("dns")):
            result = skill.lambda_handler(intent_event("WhatIntent", "is it"), None)
        self.assertIn("could not reach", self.speech(result))

    def test_empty_reply(self):
        with mock.patch.object(skill, "post_json", return_value=ok_reply("  ")):
            result = skill.lambda_handler(intent_event("WhatIntent", "is it"), None)
        self.assertIn("empty", self.speech(result))

    def test_missing_key(self):
        with mock.patch.dict(os.environ, {"OPENROUTER_API_KEY": "", "S3_PERSISTENCE_BUCKET": ""}):
            with mock.patch.object(skill, "post_json") as post:
                result = skill.lambda_handler(intent_event("WhatIntent", "is it"), None)
        post.assert_not_called()
        self.assertIn("not set up", self.speech(result))

    def test_key_read_from_s3_bucket(self):
        body = mock.Mock()
        body.read.return_value = b"sk-or-from-s3\n"
        client = mock.Mock()
        client.get_object.return_value = {"Body": body}
        fake_boto3 = mock.Mock()
        fake_boto3.client.return_value = client
        with mock.patch.dict(os.environ, {"OPENROUTER_API_KEY": "", "S3_PERSISTENCE_BUCKET": "bucket-1"}):
            with mock.patch.dict(sys.modules, {"boto3": fake_boto3}):
                self.assertEqual("sk-or-from-s3", skill.load_key())
        client.get_object.assert_called_once_with(Bucket="bucket-1", Key=skill.KEY_OBJECT)

    def test_empty_slot_asks_again(self):
        with mock.patch.object(skill, "post_json") as post:
            result = skill.lambda_handler(intent_event("WhatIntent", ""), None)
        post.assert_not_called()
        self.assertFalse(result["response"]["shouldEndSession"])

    def test_stop_ends_session(self):
        result = skill.lambda_handler(intent_event("AMAZON.StopIntent"), None)
        self.assertTrue(result["response"]["shouldEndSession"])

    def test_session_ended(self):
        result = skill.lambda_handler({"request": {"type": "SessionEndedRequest"}}, None)
        self.assertEqual({}, result["response"])

    def test_interaction_model_intents_are_handled(self):
        import json

        path = os.path.join(os.path.dirname(__file__), "..", "skill-package", "interactionModels",
                            "custom", "en-US.json")
        with open(path, encoding="utf-8") as handle:
            model = json.load(handle)["interactionModel"]["languageModel"]
        custom = {i["name"] for i in model["intents"] if not i["name"].startswith("AMAZON.")}
        self.assertEqual(set(skill.CARRIERS), custom)
        for intent in model["intents"]:
            if intent["name"] in skill.CARRIERS:
                self.assertTrue(all("{query}" in s and s.strip() != "{query}" for s in intent["samples"]))


if __name__ == "__main__":
    unittest.main()
