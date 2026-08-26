# Play Console submission pack

Everything Play asks for, filled in ahead of time. Nothing here can be submitted by an agent: the
Play Developer Publishing API can update an existing app but cannot create one, and a new app cannot
have any release — internal testing included — until the App content declarations below are answered
in the Console by the account holder.

## Prerequisites (human, one-time)

1. A Google Play developer account: $25 one-time, plus identity verification that takes days, not
   minutes. Personal accounts also need a D-U-N-S-style verification step and a public email.
2. In the Console, **Create app** → name `Cursoid`, default language, "App", "Free".
3. Register the upload certificate. It's already generated: `keytool -export -rfc` output for the
   RSA-2048 upload key, held outside this repo. Ask the agent for
   `cursoid-upload-certificate.pem` and upload it under **Setup → App signing**.
4. Create a **Service account** in Google Cloud, grant it *Release manager* in Play Console, and
   download its JSON key if you want automated uploads afterwards.

## Artifact

```bash
./gradlew bundleRelease -PplayUpload
# app/build/outputs/bundle/release/app-release.aab
```

Signed with the RSA-2048 upload key, jar-verified, 5.3 MB. `versionCode` is `1`; bump it in
`app/build.gradle.kts` for every upload, since Play rejects a repeat.

`targetSdk` is 36, which satisfies the current requirement for new apps. From 31 August 2026 new
submissions must target 36, so this stays valid; plan a bump to 37 before the 2027 deadline.

## Store listing

**App name** (30 max): `Cursoid`

**Short description** (80 max):

```
Launch and supervise Cursor cloud agents from your phone. Unofficial client.
```

**Full description** (4000 max):

```
Cursoid is an unofficial Android client for Cursor's cloud agents, built on Cursor's public Cloud
Agents API. Start an agent on one of your repositories, then close the app — it keeps working on a
cloud machine and tells you when it needs you.

WHAT YOU CAN DO
• See every agent in one inbox, filtered by what's working and what's waiting on you
• Launch an agent with a repository, starting branch, model, and Agent or Plan mode
• Watch a turn arrive live, including the assistant's replies, its reasoning, and each tool call
• Send follow-ups, or stop a turn that has gone the wrong way
• Jump straight to the branch it pushed and the pull request it opened
• Look at screenshots and logs the agent saved while it worked
• Check token spend per run
• Dictate a task instead of typing it
• Get a notification the moment an agent finishes a turn

WHAT YOU NEED
A Cursor account on a paid plan, an API key from the Cursor dashboard, and source control connected
to your Cursor account. The key is validated before it is stored, encrypted with a device-bound
Android Keystore key, and sent only to api.cursor.com. There is no Cursoid server and no analytics.

NOT SURE YET?
Tap "Explore with demo data" on the sign-in screen. Every screen works against built-in sample data,
including a simulated agent turn, with no account and no network.

HONEST LIMITS
Cursor's public API does not expose file-level diffs, so Cursoid links you to the pull request to
review changes rather than pretending to show a patch. Remote-controlling agents that run on your own
computer is also not in the public API and is not supported here.

Cursoid is not affiliated with, endorsed by, or sponsored by Anysphere, the makers of Cursor. Cursor
is their trademark.
```

## Graphics

| Asset | Requirement | File |
| --- | --- | --- |
| App icon | 512 × 512 PNG | [`docs/play/icon-512.png`](play/icon-512.png) |
| Feature graphic | 1024 × 500 PNG | [`docs/play/feature-graphic.png`](play/feature-graphic.png) |
| Phone screenshots | 2–8, min 1080 px on the short side | see below |

The screenshots in [`docs/screenshots`](screenshots) are 460 × 1024, which is under Play's minimum.
They are rendered, not captured, so re-run them at a larger size when you need store-ready files:
raise the density in the `@Config(qualifiers = …)` on `ScreenRenderTest`, or capture from the device
once it is installed. Suggested set, in order: inbox, an agent mid-turn, a finished turn with its
pull request, launching an agent, settings.

## App content declarations

**Privacy policy** — required, and the listing cannot go live without a URL. Draft text is in
[`docs/privacy-policy.md`](privacy-policy.md); host it anywhere public and paste the URL.

**Data safety** — the honest answers:

| Question | Answer |
| --- | --- |
| Does your app collect or share user data? | No |
| Is data encrypted in transit? | Yes, HTTPS only |
| Does the app have a data deletion mechanism? | N/A — nothing is collected; Settings → Sign out erases the stored key |

The API key is user-entered credential data that stays on the device and is transmitted only to the
third-party service the user is authenticating with. Nothing is sent to a developer-controlled
server, so this is "no collection" rather than a disclosed data type.

**Ads** — none. **In-app purchases** — none. **Content rating** — answer the IARC questionnaire as a
utility with no user-generated content shared between users; expect *Everyone* / PEGI 3.
**Target audience** — 18+, developers. **News app** — no. **Financial features** — none.
**Government app** — no. **Health** — no.

**Permission justifications**, in case review asks:

| Permission | Why |
| --- | --- |
| `INTERNET` | Calls `api.cursor.com` |
| `POST_NOTIFICATIONS` | Tells the user when an agent finishes a turn |
| `FOREGROUND_SERVICE` + `..._DATA_SYNC` | Holds one streaming connection open to follow a run the user asked to be notified about |
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` | Added by AndroidX WorkManager for the periodic background check |

No sensitive or restricted permissions are used: no location, contacts, camera, microphone (dictation
is delegated to the system recognizer, which keeps the permission), all-files access, or query-all-
packages.

## The name

`Cursoid` is a coined portmanteau, and the listing must not imply endorsement — hence the
"unofficial" lead in the short description and the explicit trademark disclaimer at the end of the
full description, matching the notice already shown on the app's sign-in and Settings screens. If
Anysphere objects, the rename touches the launcher label, `applicationId`, and the package; expect to
publish under a new listing if the `applicationId` changes.

## Testing tracks

Internal testing is the fastest route to a device and is exempt from the 12-testers-for-14-days rule
that gates production for new personal accounts. Add your own Google account as an internal tester,
upload the AAB, and install from the opt-in link — no cable required.
