# Lumora Alexa skill (private, development mode)

Ask the Lumora assistant through the remote's Alexa button:

> "Alexa, ask **Lumora assistant** what is a good comedy for tonight?"

Alexa recognises the speech, this skill sends the question to OpenRouter, and Alexa speaks the
answer (Fire TV also shows it as a card). The skill is never published; it stays in **Development**
and works only on devices signed in to your Amazon account.

## Limits (Amazon platform, not configurable)
- Voice in, voice out. A skill cannot open or control apps on Fire TV, so it cannot open Lumora,
  launch apps, or show answers inside Lumora.
- Every question needs the invocation: "Alexa, ask Lumora assistant …". After an answer the
  session stays open for a few seconds; press the Alexa button again to ask a follow-up
  (the last 6 turns are kept).
- Alexa waits about 8 s for an answer. Slow free models time out; the skill then says so.
- English (en-US) interaction model. You can still ask for an answer in Arabic
  ("tell me in Arabic …"), and Alexa speaks it with the en-US voice.

## What leaves your devices
| Data | Goes to |
|---|---|
| Your voice | Amazon Alexa (as for any Alexa request) |
| The recognised question and the last 6 turns | This skill (AWS Lambda run by Amazon for you), then OpenRouter and the model provider it selects |
| OpenRouter key | Stored in the skill's own S3 bucket (encrypted at rest, private to the skill); sent only to openrouter.ai |

The Lumora TV app keeps its own copy of the key; this cloud copy is separate. Use an OpenRouter
key with a **credit limit of 0** so it can only use free models.

## Setup (about 15 minutes, free)
Prerequisites: an Amazon developer account at https://developer.amazon.com signed in with the
**same Amazon account as the Fire TV**, and an OpenRouter key (ideally a second key just for Alexa,
so you can revoke it separately).

1. **Create the skill.** Alexa developer console → **Create skill**
   - Name: `Lumora`, primary locale **English (US)** (must match the language set on the Fire TV
     under Settings › Preferences › Language, or Alexa will not find the skill).
   - Type: **Other** → **Custom** → hosting: **Alexa-hosted (Python)** → template **Start from Scratch**.
2. **Interaction model.** Build tab → **Interaction Model** → **JSON Editor** → paste
   `skill-package/interactionModels/custom/en-US.json` → **Save** → **Build skill**.
3. **Code.** Code tab → replace the content of `lambda_function.py` with
   `lambda/lambda_function.py`, and `requirements.txt` with `lambda/requirements.txt`
   → **Save** → **Deploy**.
4. **Key.** Code tab → **Media storage** (S3 icon in the toolbar). In the S3 console that opens,
   inside `Media/`, create folder `lumora`, then upload a text file named `openrouter-key.txt`
   containing only the key (one line). Do not make it public.
   Create the file on your PC without committing it anywhere, then delete the local copy.
5. **Enable testing.** Test tab → set **Skill testing is enabled in** to **Development**.
   Type `ask lumora assistant what is the capital of egypt` to check it answers.
6. **On the TV.** Hold the Alexa button: "Alexa, ask Lumora assistant who directed Inception?"

Optional: a different model. Alexa-hosted skills cannot set environment variables from the console,
so the model is fixed to `openrouter/free`. To change it, edit `DEFAULT_MODEL` in
`lambda_function.py` and deploy again.

## Rotate or remove the key
- Rotate: create a new key at openrouter.ai/keys, replace `Media/lumora/openrouter-key.txt`, then
  deploy again (deploying clears the cached key), and delete the old key on OpenRouter.
- Remove the skill entirely: Alexa developer console → skill → **Delete**. This deletes its
  Lambda function and S3 bucket.

## Phrasing
Start the question with: what, who, where, when, why, how, which, can/could, is/are/does/do,
or: tell me, ask, explain, recommend, suggest, give me, help me, translate, I want to know, question.

## Tests
```powershell
# from the repository root
python -m unittest discover -s alexa-skill/tests -v
```
