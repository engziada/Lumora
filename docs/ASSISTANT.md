# Lumora Assistant — setup and behaviour (v5.2.0)

## 1. Get a free OpenRouter key
1. Sign in at https://openrouter.ai and open **Keys** → **Create key**.
2. Optional: set the key's credit limit to 0 so it can never spend money.
3. Free models are rate limited by OpenRouter (per minute and per day; the daily cap is higher
   once an account has bought credits). Limits change — check OpenRouter's rate-limit docs.

## 2. Add the key on the TV
Settings › **AI assistant** › **API key**. Typing a 70-character key with the remote is slow; from a
computer on the same network:

```powershell
adb connect <tv-ip>:5555
# Focus the key field on the TV first, then:
adb shell input text sk-or-v1-YOUR_KEY
```

Press **Save**, then **Test key** (calls `GET https://openrouter.ai/api/v1/key`; no chat request spent).

Storage: `shared_prefs/lumora_secrets.xml`, app-private, excluded from Android backup and device
transfer. The key is never logged and is displayed masked (`sk-or-…abcd`).

## 3. Opening the assistant
| Trigger | Works where | Needs |
|---|---|---|
| Floating button (end of the bottom bar) | Lumora Home | nothing |
| Remote button | any app | quick actions (one-time `pm grant … WRITE_SECURE_SETTINGS`) |
| Remote button | inside Lumora only | quick actions **off** (fallback in `MainActivity`) |

Default key codes: `SEARCH`, `VOICE_ASSIST`, `ASSIST`. **Learn** records the next button press
through the accessibility service (10 s window). If nothing arrives, Fire OS consumed the button
before apps see it (common for the Alexa button) — use the floating button or learn a different
button. A learned button no longer performs its normal action.

## 4. What is sent
Model `openrouter/free` (or the custom id). Each request carries: a system prompt (time, rules and —
if *Share installed app names* is on — up to 80 `Label (package)` entries), and the last 12 turns.
Nothing else. Replies may include `[[open:package]]`; Lumora shows **Open <app>** only for installed,
launchable packages and launches through `MainActivity`, so app locks apply.

## 5. Known limits
- Conversation lives in memory only; it is lost when the process is killed.
- The mic button appears only if a speech recognizer exists (often absent on Fire OS).
- Free-router answers come from a random free model; quality and language support vary.
