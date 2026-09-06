"""
Extracts text from screenshots so they can go through the same pipeline
as typed messages. pytesseract is just a Python wrapper — it needs the
actual Tesseract OCR engine installed as a system package to work.
See README.md for the install command.
"""

import base64
import io

import pytesseract
from PIL import Image


def extract_text(base64_image: str) -> str:
    image_bytes = base64.b64decode(base64_image)
    image = Image.open(io.BytesIO(image_bytes))
    text = pytesseract.image_to_string(image)
    return text.strip()
