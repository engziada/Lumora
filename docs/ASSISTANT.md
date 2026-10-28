# Lumora Assistant — setup and behaviour (v5.3.0)

## 1. Get a free OpenRouter key
1. Sign in at https://openrouter.ai and open **Keys** → **Create key**.
2. Optional: set the key's credit limit to 0 so it can never spend money.
3. Free models are rate limited by OpenRouter (per minute and per day; the daily cap is higher
   once an account has bought credits). Limits change — check OpenRouter's rate-limit docs.

## 2. Add the key on the TV
Settings › **AI assistant** › **Enter key from phone**: scan the QR code with the phone, paste the
key on the page that opens, tap **Send**. The key travels only over your home network to the TV,
is saved, and tested.

Alternatives: **API key** (type with the remote), or from a computer:

```powershell
adb connect <tv-ip>:5555
# Focus the key field on the TV first, then:
adb shell input text sk-or-v1-YOUR_KEY
```

**Test key** calls `GET https://openrouter.ai/api/v1/key` (no chat request spent).

Storage: `shared_prefs/lumora_secrets.xml`, app-private, excluded from Android backup and device
transfer. The key is never logged and is displayed masked (`sk-or-…abcd`).

## 3. Opening the assistant
| Trigger | Works where | Needs |
|---|---|---|
| Floating button (end of the bottom bar) | Lumora Home | nothing |
| **Hold ☰ Menu** (about 0.6 s) | any app | quick actions (one-time `pm grant … WRITE_SECURE_SETTINGS`) |
| **Hold ☰ Menu** | inside Lumora only | quick actions **off** (fallback in `MainActivity`) |

A short Menu press still works as Menu everywhere. Inside Lumora the app menu now opens on
*release* of Menu, so holding Menu opens only the assistant. Other apps that act the moment Menu
is pressed down may also see that press; this cannot be avoided because an accessibility service
cannot re-inject a key it swallowed.

Why not the Alexa button or other buttons: Fire OS delivers the Alexa/microphone, Home and
app-shortcut buttons (Netflix, Prime Video, …) to the system only; no app receives them. The
v5.2.0 *Learn* feature was removed for that reason. For Alexa, see section 6.

## 4. Talk instead of typing: phone link
Fire OS gives apps no speech recognizer and the remote's microphone belongs to Alexa. The phone
link uses your phone's keyboard dictation instead (English and Arabic work with Gboard and iOS).

1. In the assistant panel press the **phone** button (left of Send).
2. Scan the QR code with the phone camera (or type the address shown, then the 6-digit code).
3. On the page, tap the text box, tap the keyboard's microphone, speak, tap **Send**.
4. The question appears on the TV and is answered there. Keep sending from the phone; the phone
   icon stays highlighted while the link is open.

| Property | Value |
|---|---|
| Listens on | the TV's private IPv4 address only (Wi-Fi or Ethernet), port 8765 (8766–8775 if busy) |
| Open while | the assistant panel (or the key screen) is on screen; closes when it leaves the screen |
| Idle timeout | 10 minutes |
| Pairing | 6-digit code, in the QR code's URL fragment (never sent to the server); 5 wrong codes rotate it |
| Limits | headers 8 KB, body 8 KB, 2000 characters per message, 30 messages per minute |
| Other checks | `Host` must be the TV's address (blocks DNS-rebinding pages); `no-store`, CSP, `nosniff` |
| Encryption | none (plain HTTP on the home network). Do not use on untrusted Wi-Fi. |

Troubleshooting: if the phone cannot open the page, check both devices are on the same network and
that the router does not isolate Wi-Fi clients ("AP/client isolation", common on guest networks).

## 5. What is sent to OpenRouter
Model `openrouter/free` (or the custom id). Each request carries: a system prompt (time, rules and —
if *Share installed app names* is on — up to 80 `Label (package)` entries), and the last 12 turns.
Nothing else. Replies may include `[[open:package]]`; Lumora shows **Open <app>** only for installed,
launchable packages and launches through `MainActivity`, so app locks apply.

## 6. Alexa
A private Alexa skill can answer through the Alexa button: "Alexa, ask Lumora assistant …". It is
voice only (Amazon does not let skills control apps on Fire TV) and needs its own copy of the
OpenRouter key in the skill's cloud storage. Setup: [`alexa-skill/README.md`](../alexa-skill/README.md).

## 7. Known limits
- Conversation lives in memory only; it is lost when the process is killed.
- The TV's mic button appears only if a speech recognizer exists (not on Fire OS).
- The phone link stops when the screen saver starts or another app opens.
- Free-router answers come from a random free model; quality and language support vary.
