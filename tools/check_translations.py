"""Check Minecraft language keys and format arguments without starting the game."""
from collections import Counter
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TOKENS = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
FORMATS = re.compile(r'%(?:(\d+)\$)?s|%%')


def resources(path):
    result = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        assert key not in result, "Duplicate key: {}:{} {}".format(path, number, key)
        assert value.strip(), "Empty translation: " + key
        result[key] = value
    return result


def placeholders(value):
    return Counter(match.group() for match in FORMATS.finditer(value) if match.group() != "%%")


def argument_count(source, start):
    # Starts immediately after the literal key. Count top-level commas, ignoring nested calls/strings.
    source = source[start:]
    masked = TOKENS.sub(lambda match: " " * len(match.group()), source)
    depth = 0
    count = 0
    for char in masked:
        if char == ")" and depth == 0:
            return count
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            count += 1
    raise AssertionError("Unclosed translation call")


def main():
    folder = ROOT / "src/main/resources/assets/ltsmodelshare/lang"
    english = resources(folder / "en_us.lang")
    supported = {"en_us", "zh_cn", "zh_tw", "ja_jp", "ko_kr", "de_de", "fr_fr"}
    assert supported <= {path.stem for path in folder.glob("*.lang")}, "Missing supported language packs"
    packs = list(folder.glob("*.lang"))
    for path in packs:
        translated = resources(path)
        assert english.keys() == translated.keys(), "Language keys do not match: " + path.name
        for key in english:
            assert placeholders(english[key]) == placeholders(translated[key]), "Placeholder mismatch: " + path.name + ": " + key
    calls = 0
    for path in (ROOT / "src/main/java").rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        for match in re.finditer(r'\btr\("(ltsmodelshare\.[\w.]+)"', source):
            key = match.group(1)
            assert key in english, "Missing translation: " + key
            expected = sum(placeholders(english[key]).values())
            actual = argument_count(source, match.end())
            assert expected == actual, "{}: {} expects {} arguments, got {}".format(path.name, key, expected, actual)
            calls += 1
        for key in re.findall(r'"(ltsmodelshare\.[\w]+\.[\w.]+)"', source):
            assert key in english, "Missing translation: " + key
    print("Translation checks passed: {} languages, {} keys per language, {} formatted calls".format(len(packs), len(english), calls))


if __name__ == "__main__":
    main()
