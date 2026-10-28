"""Decode-check qr.py with OpenCV and print the QrCodeTest fixtures."""
import hashlib
import os
import random
import string
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from qr import encode  # noqa: E402


def render(modules, scale, quiet=4):
    size = len(modules)
    img = np.full(((size + 2 * quiet) * scale,) * 2, 255, np.uint8)
    for y in range(size):
        for x in range(size):
            if modules[y][x]:
                img[(y + quiet) * scale:(y + quiet + 1) * scale, (x + quiet) * scale:(x + quiet + 1) * scale] = 0
    return img


def decodes(modules, text, detectors):
    for scale in (6, 8, 10):
        image = render(modules, scale)
        for detector in detectors:
            if detector.detectAndDecode(image)[0] == text:
                return True
    return False


random.seed(7)
samples = ["A", "http://192.168.1.23:8765/#c=123456", "http://10.0.0.5:8770/#c=000001", "x" * 106,
           "Lumora لومورا", "http://192.168.100.200:8765/#c=999999"]
for _ in range(60):
    samples.append("".join(random.choice(string.printable[:94]) for _ in range(random.randint(1, 106))))
detectors = [cv2.QRCodeDetector(), cv2.QRCodeDetectorAruco()]
failures = 0
for text in samples:
    for mask in [-1] + list(range(8)):
        modules, _ = encode(text, mask)
        if not decodes(modules, text, detectors):
            failures += 1
            print("FAIL", mask, repr(text))
print("checked", len(samples) * 9, "failures", failures)

for text in ["HELLO", "http://192.168.1.23:8765/#c=123456", "x" * 106]:
    modules, mask = encode(text)
    rows = "\n".join("".join("1" if c else "0" for c in row) for row in modules)
    print(repr(text[:40]), len(modules), mask, hashlib.sha256(rows.encode()).hexdigest())
