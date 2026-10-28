# Lumora Assistant — setup and behaviour (v5.4.0)

## 1. Get a free OpenRouter key
1. Sign in at https://openrouter.ai and open **Keys** → **Create key**.
2. Optional: set the key's credit limit to 0 so it can never spend money.
3. Free models are rate limited by OpenRouter (per minute and per day; the daily cap is higher
   once an account has bought credits). Limits change — check OpenRouter's rate-limit docs.

The key is needed for questions only. The plain TV commands in section 6 work without a key.

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

A short Menu press still works as Menu everywhere. Inside Lumora the app menu opens on *release*
of Menu, so holding Menu opens only the assistant. Other apps that act the moment Menu is pressed
down may also see that press; an accessibility service cannot re-inject a key it swallowed.

Why not the Alexa button or other buttons: Fire OS delivers the Alexa/microphone, Home and
app-shortcut buttons (Netflix, Prime Video, …) to the system only; no app receives them.

## 4. Talk instead of typing: phone link
Fire OS gives apps no speech recognizer and the remote's microphone belongs to Alexa. The phone
link uses your phone's keyboard dictation instead (English and Arabic work with Gboard and iOS).

1. In the assistant panel press the **phone** button (left of Send).
2. Scan the QR code with the phone camera (or type the address shown, then the 6-digit code).
3. On the page, tap the text box, tap the keyboard's microphone, speak, tap **Send**.
4. The text appears on the TV and is answered or carried out there. The phone icon stays
   highlighted while the link is open.

Note: actions that leave the panel (play, home, open an app, search, settings) close the panel,
and closing the panel closes the phone link. Volume, pause, next and similar keep it open.

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
Model `openrouter/free` (or the custom id). Each request carries: a system prompt (time, rules, the
TV action list when control is on and — if *Share installed app names* is on — up to 80
`Label (package)` entries), and the last 12 turns. Nothing else. Plain commands (section 6) are
handled on the TV and are not sent.

## 6. TV control
Settings › AI assistant › **TV control** (default **On**). Off = answers only, no actions.

### 6.1 Plain commands (instant, no key, no network)
The whole message must be the command; a polite word is allowed ("please", "Lumora", "لو سمحت",
"من فضلك", "يا لومورا"). Arabic spelling variants (أ/إ/آ, ة/ه, ى/ي, diacritics) are accepted.

| Action | English examples | Arabic examples |
|---|---|---|
| Pause | pause, stop, hold on | وقف، ايقاف، استوب |
| Play | play, resume, continue | شغل، كمل، استمر |
| Next | next, skip, next episode | التالي، الحلقة التالية |
| Previous | previous, previous episode | السابق، الحلقة السابقة |
| Fast forward / Rewind | fast forward, rewind | قدم، ترجيع |
| Volume up / down (2 steps) | volume up, louder / volume down, quieter | علي الصوت / وطي الصوت |
| Mute / Unmute | mute / unmute | اكتم / فك الكتم |
| Back / Home | back / home | ارجع / الرئيسية |
| Screenshot / Power menu | screenshot / power menu | لقطة شاشة / قائمة الطاقة |
| Settings pages | settings, wifi settings, bluetooth settings, display settings, sound settings | الاعدادات، اعدادات الواي فاي |

The full list is in `assistant/LocalCommands.java`.

### 6.2 Model-driven actions (needs the key)
Anything else goes to the model, which may answer with directives `[[do:name|arg|query]]` that
Lumora checks against a fixed whitelist (`assistant/TvAction.java`):

| Group | Actions | Notes |
|---|---|---|
| Media | play, pause, play_pause, next, previous, stop, forward, rewind | sent as media keys to the active player |
| Volume | volume_up / volume_down (1–10 steps, default 2), mute, unmute | music stream |
| System | back, home, screenshot, power_menu | need quick actions; screenshot needs Android 9+ |
| Settings | settings: main, wifi, bluetooth, display, sound, apps, about | falls back to main settings |
| Theme | theme: amber, copper, rose, moonlight | Lumora accent |
| Search | search: installed package + query (≤ 200 characters) | e.g. "search Netflix for Dark"; locked apps ask for the code |
| Open | `[[open:package]]` | installed, launchable apps only; app locks apply |

At most 4 actions per reply; duplicates are dropped; unknown names are ignored.

### 6.3 Limits
- **Volume over HDMI-CEC / IR**: when the TV or soundbar controls volume, Android's volume is fixed
  and Lumora reports "volume is controlled by the TV". Use the remote's volume keys.
- Media keys reach the app that owns the active media session. Some players ignore some keys
  (for example forward/rewind).
- Back, Home, Screenshot and Power menu need quick actions (README › One-time setup).
- Lumora cannot press arbitrary buttons, read the screen or type into other apps.

## 7. Known limits
- Conversation lives in memory only; it is lost when the process is killed.
- The TV's mic button appears only if a speech recognizer exists (not on Fire OS).
- The phone link stops when the screen saver starts or another app opens.
- Free-router answers come from a random free model; quality, language support and adherence to
  the action format vary.
