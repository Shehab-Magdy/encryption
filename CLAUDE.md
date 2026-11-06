# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Hardware-bound licensing system (Python 3.14, PyQt5). Two desktop apps share one core module, plus an Android port of the admin tool. [prompt.md](prompt.md) is the original spec (UI strings are Arabic; the contact phone number is hardcoded in the client UI). No git repo, no tests, no linter configured.

## Commands

Use the local virtualenv (`.venv`, contains PyQt5, PyInstaller):

```
source .venv/bin/activate
python client_app.py        # client: shows license status / hardware code / activation input
python admin_app.py         # admin: hardware code -> activation code
pyinstaller ClientApp.spec          # -> dist/ClientApp
pyinstaller LicenseGenerator.spec   # -> dist/LicenseGenerator
```

`build/` and `dist/` are PyInstaller output (also contain sample `license.json` and `licenses_db.json`).

## Architecture

- [license_core.py](license_core.py) — `LicenseManager`, the only place with licensing logic; no UI deps and intended to be copy-pasted into other projects (kept byte-identical to masar's `utils/license_core.py`). Keep it that way (the UI files must not duplicate logic).
  - `get_hardware_id()` = SHA-256 of `|`-joined MAC, OS machine GUID (Windows registry / `/etc/machine-id` / macOS `system_profiler`), and `platform.processor()`, truncated to 80 bits and encoded as a short human-readable code (Crockford Base32, grouped `XXXX-XXXX-XXXX-XXXX`). Any fingerprint component change yields a new code.
  - `generate_activation_code(hardware_id)` = HMAC-SHA256 of the normalized hardware code, truncated and encoded the same way — the activation code the admin sends back.
  - `activate_license`/`validate_license` recompute the current hardware code and expected activation code locally and compare via `hmac.compare_digest`; no decrypt step. `license.json` stores `hardware_id` and `activation_code`.
  - The HMAC key is hardcoded (base64) in `LicenseManager.__init__`, so the admin and client must ship identical keys; changing it invalidates all existing licenses. The key being in the client means the client binary can forge codes (a known limitation of the symmetric choice; the spec prefers RSA for a stronger model). Truncating to 80 bits is a usability tradeoff, not a meaningful security regression given that existing limitation.
- [client_app.py](client_app.py) — PyQt5 UI. Flow: `validate_license()` → show green status, else show `get_hardware_id()` for the user to send to the vendor, then `activate_license(code)`. `license.json` lives next to `sys.executable` (note: when run via plain `python`, that is the interpreter's directory, not the project dir).
- [admin_app.py](admin_app.py) — PyQt5 UI. Takes the client's hardware code → `generate_activation_code` → shows the code, and appends a record (`ID`, `HardwareID`, `ActivationCode`, `created_at`) to `licenses_db.json` next to `sys.executable`.
- [android/src/com/licensegen/MainActivity.java](android/src/com/licensegen/MainActivity.java) — Java port of `admin_app.py`'s `generate_activation_code` (same HMAC key, truncation, and Base32 grouping), so activation codes can be generated from a phone.

Only `client_app.py` has a hand-written-looking spec that is tracked in [ClientApp.spec](ClientApp.spec); both specs build one-file console executables from the respective script.
