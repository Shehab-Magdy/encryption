**Reusable Hardware Licensing System (Python Desktop)**

### 🎯 Objective

Build **two Python desktop applications** implementing a **hardware-bound licensing system** with:

* **Method A** → symmetric encryption (e.g., AES / Fernet)
* **Method B** → secure license generation (e.g., RSA or HMAC)

Additionally:

* The **client-side logic must be modular and easily copy-paste reusable** across other applications.
* The  **admin tool must persist license records in a structured JSON database** .

---

# 🧩 Application 1: Client License Validator (Reusable Module + UI)

## 📌 Core Requirement (IMPORTANT)

Design the client-side as  **two layers** :

### 1. 🔹 Reusable Core Module (MANDATORY)

Create a **standalone Python module** (e.g., `license_core.py`) that:

* Contains ALL licensing logic
* Has **no UI dependencies**
* Can be copied directly into any project

### Required API (clean interface)

```python
class LicenseManager:
    def get_hardware_id(self) -> str: ...
    def encrypt_A(self, data: str) -> str: ...
    def decrypt_A(self, data: str) -> str: ...
    def encrypt_B(self, data: str) -> str: ...
    def decrypt_B(self, data: str) -> str: ...
  
    def load_license(self) -> dict: ...
    def save_license(self, data: dict) -> None: ...
  
    def validate_license(self) -> bool: ...
    def activate_license(self, activation_code: str) -> bool: ...
```

👉 This module must:

* Be **plug-and-play**
* Require minimal configuration
* Avoid tight coupling with UI or framework

---

### 2. 🔹 Desktop UI Layer

* Built using **PyQt5 or PySide6**
* Uses the `LicenseManager` module only (no duplicated logic)

---

## 📌 Functional Requirements

### 1. Hardware Fingerprint

Collect:

* CPU ID
* MAC Address
* Disk Serial Number
* OS Machine GUID

👉 Normalize + concatenate → hash (SHA-256) → final HID

---

### 2. License File

File: `license.json`

```json
{
  "HID_A": "<encrypted_with_A>",
  "HID_B": "<encrypted_with_B>"
}
```

---

### 3. First Run Flow

If no license exists:

1. Generate HID
2. Encrypt using **Method A**
3. Display:
   ```
   [Encrypted HID]
   برجاء ارسال تلك الاحرف الى الرقم التالي: ٠١٠٠٩٣٥٢٨٢٣
   ```
4. Wait for activation code input

---

### 4. Activation Flow

1. Decrypt user input using **Method B**
2. Compare with current HID

#### ✔ If valid:

* Save:
  * `HID_A` (Method A)
  * `HID_B` (Method B)
* Allow execution

#### ❌ If invalid:

Show (RED):

```
هذا جهاز جديد يجب ارسال الكود الى ٠١٠٠٩٣٥٢٨٢٣
```

---

### 5. Subsequent Runs

* Decrypt `HID_A`
* Compare with current HID

#### ✔ Match → allow

#### ❌ Mismatch → block + show Arabic message

---

### 6. UI Requirements

* Minimal interface
* Arabic support (UTF-8)
* Status coloring (green/red)

---

# 🧩 Application 2: Admin License Generator + JSON Database

## 📌 Functional Requirements

### 1. Input

* Accept encrypted HID (Method A)

---

### 2. Processing

1. Decrypt using Method A
2. Generate activation code using Method B

---

### 3. Output

* Show activation code
* Copy-to-clipboard support

---

## 💾 4. JSON Database Storage (NEW REQUIREMENT)

Maintain a **local JSON database file** (e.g., `licenses_db.json`)

### Structure:

```json
[
  {
    "ID": 1,
    "data": {
      "CPU_ID": "",
      "MAC_Address": "",
      "Disk_Serial_Number": "",
      "OS_Machine": "",
      "GUID": ""
    },
    "HID_raw": "<hashed_or_normalized_value>",
    "HID_A": "<encrypted_with_A>",
    "HID_B": "<encrypted_with_B>",
    "created_at": "<timestamp>"
  }
]
```

---

### 📌 Behavior

When generating a license:

1. Decrypt HID (Method A)
2. (Optional but recommended) Reconstruct or store parsed components if available
3. Save:
   * Full hardware breakdown (if derivable)
   * Raw HID (hashed)
   * Encrypted versions (A + B)
   * Incremental ID
   * Timestamp

---

### 📌 Notes on Hardware Data

Since HID is hashed:

* You may NOT fully reverse to original hardware fields
* Therefore:
  * Either:
    * Store only HID + encrypted values
  * OR:
    * Modify Client App to optionally send structured hardware data

👉 Prefer storing:

* HID + encrypted values for consistency

---

# 🔐 Encryption Requirements

### Method A (Client-safe reversible)

* Fernet (AES)
* Used for:
  * Local storage
  * Transport to admin tool

---

### Method B (Authority validation)

Choose:

* RSA (preferred for stronger model)
* OR HMAC (simpler)

Used for:

* Activation code generation
* Anti-forgery

---

# 🛡️ Security & Engineering Requirements

* Hash hardware data before encryption
* Obfuscate keys in client module
* Ensure module reuse without exposing secrets
* Validate all inputs
* Handle corrupted license files gracefully
* Log errors internally

---

# 📦 Packaging

* Use **PyInstaller**
* Outputs:
  * `ClientApp.exe`
  * `LicenseGenerator.exe`

---

# 🧪 Testing Scenarios

* First run activation
* Valid/invalid activation
* License reuse across machines (must fail)
* Tampered license.json
* Empty input
* DB persistence validation

---

# 🚀 Optional Enhancements

* License expiry
* Device count limits
* Online verification API
* Admin search/filter UI
* Export license records

---

## ⚠️ Notes for Implementation AI

* Prioritize **clean architecture**
* Strict separation:

  * Core logic (reusable)
  * UI
  * Storage
* Code must be readable, modular, and production-ready
* Avoid hardcoding values in multiple places
