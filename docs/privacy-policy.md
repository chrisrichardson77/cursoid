# Cursoid privacy policy

_Last updated: 26 August 2026_

Cursoid is an unofficial Android client for Cursor's cloud agents. It has no backend of its own.

## What Cursoid stores

Your Cursor API key, and a small amount of local state: whether demo mode and notifications are on, a
cache of prompts you have sent so conversations can show both sides, and a snapshot of which agents
were working so the app can tell when one has finished.

All of it stays on your device. The API key is encrypted with an AES-GCM key generated inside the
Android Keystore, so it cannot be read off the device's storage without the device. Nothing is
included in cloud backups or device transfers.

## What Cursoid sends, and where

Requests go to `api.cursor.com` over HTTPS, carrying your API key so Cursor can identify you, and the
prompts and options you enter. Cursor's handling of that data is governed by
[Cursor's privacy policy](https://cursor.com/privacy).

Images shown in the app — screenshots and other artifacts your agents produced — are fetched from the
storage URLs Cursor's API returns.

If you open a pull request, a repository, or a documentation link, your browser goes to that address.

That is the complete list. There is no Cursoid server, no analytics, no crash reporting, no
advertising, and no third-party SDK that phones home.

## What Cursoid collects about you

Nothing. The developer of Cursoid receives no data from your use of the app and has no way to see who
is using it.

## Deleting your data

Settings → "Sign out and forget key" erases the stored key and local state. Uninstalling the app
removes everything, including the Keystore key used to encrypt it.

## Permissions

Notifications are used to tell you when an agent finishes a turn. A foreground service holds one
streaming connection open when you ask to be notified about a specific run. Dictation is handed to
Android's own speech recognizer, so Cursoid never requests microphone access. No location, contacts,
camera, or file access is requested.

## Children

Cursoid is a developer tool and is not directed at children under 13.

## Trademarks

Cursoid is not affiliated with, endorsed by, or sponsored by Anysphere. Cursor is their trademark.

## Contact

Raise an issue on the project repository.
