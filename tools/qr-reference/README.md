# QR reference encoder

`qr.py` is a Python mirror of `app/.../assistant/QrCode.java` (byte mode, ECC level M, versions 1-6).
`verify.py` renders codes for 66 strings with every mask and decodes them with OpenCV, then prints
the module-grid SHA-256 values used as fixtures in `QrCodeTest`.

```powershell
python -m pip install opencv-python numpy
python tools/qr-reference/verify.py
```

Last run (2026-10-28): all 594 codes decoded (OpenCV `QRCodeDetector` or `QRCodeDetectorAruco`,
at 6, 8 or 10 px per module); fixtures match `QrCodeTest`.
Not part of the app build or CI.
