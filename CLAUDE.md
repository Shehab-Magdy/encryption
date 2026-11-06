# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Hardware-bound licensing system (Python 3.14, PyQt5, `cryptography`). Two desktop apps share one core module. [prompt.md](prompt.md) is the original spec (UI strings are Arabic; the contact phone number is hardcoded in the client UI). No git repo, no tests, no linter configured.

## Commands

Use the local virtualenv (`.venv`, contains PyQt5, cryptography, PyInstaller):

```
source .venv/bin/activate
python client_app.py        # client: shows license status / encrypted HID / activation input
python admin_app.py         # admin: encrypted HID -> activation code
pyinstaller ClientApp.spec          # -> dist/ClientApp
pyinstaller LicenseGenerator.spec   # -> dist/LicenseGenerator
```

`build/` and `dist/` are PyInstaller output (also contain sample `license.json` and `licenses_db.json`).

## Architecture

- [license_core.py](license_core.py) — `LicenseManager`, the only place with licensing logic; no UI deps and intended to be copy-pasted into other projects. Keep it that way (the UI files must not duplicate logic).
  - HID = SHA-256 of `|`-joined MAC, OS machine GUID (Windows registry / `/etc/machine-id` / macOS `system_profiler`), and `platform.processor()`. Any component change yields a new HID.
  - **Method A** = Fernet (reversible): client encrypts its HID to send to the admin; also stored as `HID_A` in `license.json`.
  - **Method B** = HMAC-SHA256 activation code of form `<HID>|<hexsig>`; admin generates it, client verifies and compares to its own HID.
  - Both keys are hardcoded (base64) in `LicenseManager.__init__`, so the admin and client must ship identical keys; changing them invalidates all existing licenses. The HMAC key being in the client means the client binary can forge codes (a known limitation of the symmetric choice; the spec prefers RSA for a stronger model).
  - `validate_license` only checks `HID_A` decrypts to the current HID (`HID_B` is stored but not re-verified).
- [client_app.py](client_app.py) — PyQt5 UI. Flow: `validate_license()` → show green status, else show `encrypt_A(HID)` for the user to send to the vendor, then `activate_license(code)`. `license.json` lives next to `sys.executable` (note: when run via plain `python`, that is the interpreter's directory, not the project dir).
- [admin_app.py](admin_app.py) — PyQt5 UI. Takes the client's Method A string → `decrypt_A` → `encrypt_B` → shows the code, and appends a record (`ID`, `HID_raw`, `HID_A`, `HID_B`, `created_at`) to `licenses_db.json` next to `sys.executable`.

Only `client_app.py` has a hand-written-looking spec that is tracked in [ClientApp.spec](ClientApp.spec); both specs build one-file console executables from the respective script.
