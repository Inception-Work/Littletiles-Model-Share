"""Copy existing website built-in tag translations; never translate or change tag codes."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--web", type=Path, default=ROOT.parent / "inception-work" / "Sources" / "web")
    parser.add_argument("--check", action="store_true", help="Verify without writing")
    args = parser.parse_args()
    translations = {}
    for locale in ("zh-cn", "zh-tw", "en", "ja", "ko", "de", "fr"):
        source = args.web / "src" / "i18n" / "locales" / (locale + ".ts")
        matches = re.findall(r'^\s*"?tag_([\w-]+)"?:\s*("(?:\\.|[^"\\])*")\s*,?\s*$', source.read_text(encoding="utf-8"), re.M)
        if not matches:
            raise SystemExit("No built-in tag translations found in " + str(source))
        translations[locale] = {code: json.loads(value) for code, value in matches}
    if any(set(tags) != set(translations["en"]) for tags in translations.values()):
        raise SystemExit("Website tag translation keys differ between languages")
    output = ROOT / "src/main/resources/assets/ltsmodelshare/site_tag_fallbacks.json"
    content = json.dumps(translations, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.check:
        if output.read_text(encoding="utf-8") != content:
            raise SystemExit("Website tag fallback translations need syncing")
    else:
        output.write_text(content, encoding="utf-8")
    print("Website tag translations match: {} languages, {} tags each".format(len(translations), len(translations["en"])))


if __name__ == "__main__":
    main()
