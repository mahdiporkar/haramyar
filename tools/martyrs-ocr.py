#!/usr/bin/env python3
"""Stage unverified Arabic OCR text from a scanned martyr book, preserving PDF page numbers.

This utility deliberately does NOT translate, upload, or publish anything. Its
output must be proofread against the source PDF before being used for translation.
Requires Poppler (pdfinfo + pdftoppm) and Tesseract with the Arabic model.
"""

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path


def run(*command: str) -> str:
    result = subprocess.run(command, check=True, capture_output=True, text=True)
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", required=True, type=Path, help="Arabic source PDF")
    parser.add_argument("--output", required=True, type=Path, help="Separate OCR work directory")
    parser.add_argument("--first-page", type=int, default=1)
    parser.add_argument("--last-page", type=int, help="PDF page number (inclusive); defaults to final page")
    parser.add_argument("--dpi", type=int, default=180)
    parser.add_argument("--tessdata-dir", type=Path, help="Directory containing ara.traineddata")
    parser.add_argument("--resume", action="store_true", help="Reuse existing OCR output only if source SHA-256 matches")
    args = parser.parse_args()

    pdf = args.pdf.resolve(strict=True)
    if not pdf.is_file() or pdf.suffix.lower() != ".pdf":
        parser.error("A valid PDF file is required")
    with pdf.open("rb") as source:
        if source.read(5) != b"%PDF-":
            parser.error("A valid PDF file is required")
    info = run("pdfinfo", str(pdf))
    page_match = re.search(r"^Pages:\s*(\d+)$", info, re.MULTILINE)
    if not page_match:
        parser.error("Cannot determine PDF page count")
    pages = int(page_match.group(1))
    final_page = args.last_page or pages
    if not (1 <= args.first_page <= final_page <= pages):
        parser.error(f"Select an inclusive PDF page range between 1 and {pages}")
    if not (100 <= args.dpi <= 400):
        parser.error("DPI must be between 100 and 400")
    digest = hashlib.sha256()
    with pdf.open("rb") as data:
        for chunk in iter(lambda: data.read(1024 * 1024), b""):
            digest.update(chunk)
    source_sha = digest.hexdigest()

    output = args.output.resolve()
    if output == pdf.parent or output == pdf:
        parser.error("OCR output must be in a separate directory")
    output.mkdir(parents=True, exist_ok=True)
    manifest_path = output / "ocr-manifest.json"
    existing = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else None
    if existing and existing.get("source_sha256") != source_sha:
        parser.error("Refusing to mix OCR results from different source PDF files")
    if existing and not args.resume:
        parser.error("Output already exists; pass --resume to append matching source pages")

    completed = dict(existing.get("pages", {})) if existing else {}
    for page in range(args.first_page, final_page + 1):
        filename = f"page-{page:04d}.txt"
        target = output / filename
        if args.resume and str(page) in completed and target.is_file() and target.stat().st_size > 0:
            continue
        with tempfile.TemporaryDirectory(prefix="salim-ocr-") as temporary:
            prefix = str(Path(temporary) / "page")
            run("pdftoppm", "-f", str(page), "-l", str(page), "-r", str(args.dpi), "-png", "-singlefile", str(pdf), prefix)
            cmd = ["tesseract", prefix + ".png", "stdout", "-l", "ara", "--psm", "3"]
            if args.tessdata_dir:
                cmd.extend(["--tessdata-dir", str(args.tessdata_dir.resolve(strict=True))])
            ocr = run(*cmd).strip()
        # OCR does not certify accuracy, even if characters look readable.
        staged = target.with_suffix(".tmp")
        staged.write_text(ocr + "\n", encoding="utf-8")
        staged.replace(target)
        completed[str(page)] = {"file": filename, "status": "OCR_UNVERIFIED", "characters": len(ocr)}
        manifest = {"source_filename": pdf.name, "source_sha256": source_sha, "pdf_pages": pages,
                    "ocr_language": "ara", "generated_at": datetime.now(timezone.utc).isoformat(), "pages": completed,
                    "notice": "Proofread every page against the original image before translating or publishing."}
        staged_manifest = manifest_path.with_suffix(".tmp")
        staged_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        staged_manifest.replace(manifest_path)
        print(f"PDF page {page}/{pages}: {len(ocr)} unverified characters")

    print(f"Completed {len(completed)} staged pages in {output}; all require human review.")


if __name__ == "__main__":
    main()
