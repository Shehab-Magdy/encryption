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

1. The client builds a hardware ID (HID): SHA-256 of the MAC address, OS machine GUID and CPU info, truncated to 80 bits and encoded as a short, human-readable code (Crockford Base32, grouped like `XK3F-9QPL-7ZRT-M2CD`). This is shown to the user, who sends it to the vendor.
2. The admin tool HMAC-SHA256-signs that hardware code with the shared key, truncates the signature the same way, and encodes it as another short code — the activation code.
3. The user enters the activation code in the client. The client recomputes its own hardware code and the expected activation code for it, and compares; if they match, `license.json` is saved with `hardware_id` and `activation_code`.
4. On later runs the client recomputes both and compares again. A copied `license.json` on another machine fails because its hardware code differs.

## Setup

Requires Python 3.14 (or a compatible 3.x) and PyQt5.

```bash
python -m venv .venv
source .venv/bin/activate
pip install PyQt5 pyinstaller
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
    hardware_id = lm.get_hardware_id()  # send to vendor
    # ...later, with the activation code from the vendor:
    lm.activate_license(code)
```

## Security notes

- The HMAC key is hardcoded in `LicenseManager.__init__`. The admin and client must use identical keys, and changing it invalidates all existing licenses.
- Because the HMAC key ships inside the client, a determined attacker could extract it and forge activation codes. For stronger protection, switch to RSA: sign with a private key kept only in the admin tool and verify with a public key in the client.
- Truncating the hardware code and signature to 80 bits keeps codes short to type; this is a usability tradeoff, not a weakening of the scheme's actual security margin, since the embedded HMAC key is already the binding constraint (see point above).
