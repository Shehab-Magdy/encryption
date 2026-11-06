# Hardware Licensing System

A hardware-bound licensing system for Python desktop apps. It consists of a client that locks itself to one machine and an admin tool that issues activation codes.

## Components

| File | Purpose |
|------|---------|
| `license_core.py` | `LicenseManager`: all licensing logic, no UI dependencies, copy-paste reusable |
| `client_app.py` | PyQt5 client: shows license status, the encrypted hardware ID, and an activation input |
| `admin_app.py` | PyQt5 admin tool: turns a client's encrypted ID into an activation code and records it in `licenses_db.json` |
| `prompt.md` | Original specification |

## How it works

1. The client builds a hardware ID (HID): SHA-256 of the MAC address, OS machine GUID and CPU info.
2. **Method A** (Fernet): the client encrypts the HID and shows it to the user, who sends it to the vendor.
3. The admin tool decrypts it with Method A and creates an activation code with **Method B** (HMAC-SHA256, format `<HID>|<signature>`).
4. The user enters the code in the client. If the signature is valid and the HID matches this machine, `license.json` is saved with `HID_A` and `HID_B`.
5. On later runs the client decrypts `HID_A` and compares it with the current HID. A copied `license.json` on another machine fails.

## Setup

Requires Python 3.14 (or a compatible 3.x), PyQt5 and `cryptography`.

```bash
python -m venv .venv
source .venv/bin/activate
pip install PyQt5 cryptography pyinstaller
```

## Usage

```bash
python client_app.py   # client
python admin_app.py    # admin / license generator
```

`license.json` and `licenses_db.json` are stored next to `sys.executable`. Under plain `python` that is the interpreter's folder, not the project folder. In packaged builds it is the executable's folder.

### Building executables

```bash
pyinstaller ClientApp.spec          # dist/ClientApp
pyinstaller LicenseGenerator.spec   # dist/LicenseGenerator
```

## Reusing the core module

Copy `license_core.py` into your project:

```python
from license_core import LicenseManager

lm = LicenseManager(license_file='license.json')
if not lm.validate_license():
    encrypted_hid = lm.encrypt_A(lm.get_hardware_id())  # send to vendor
    # ...later, with the activation code from the vendor:
    lm.activate_license(code)
```

## Security notes

- The Fernet and HMAC keys are hardcoded in `LicenseManager.__init__`. The admin and client must use identical keys, and changing them invalidates all existing licenses.
- Because the HMAC key ships inside the client, a determined attacker could extract it and forge activation codes. For stronger protection, switch Method B to RSA: sign with a private key kept only in the admin tool and verify with a public key in the client.
- `validate_license` checks only `HID_A`; `HID_B` is stored but not re-verified.
