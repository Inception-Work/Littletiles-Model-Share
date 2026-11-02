from pathlib import Path
for rel in ["src/main/java/work/inception/ltsmodelshare/LtsModelShare.java",
            "src/main/java/work/inception/ltsmodelshare/gui/GuiUpload.java"]:
    p = Path(rel); s = p.read_text(encoding="utf-8")
    s = s.replace('"1.20.1"', '"1.20.2"')
    p.write_text(s, encoding="utf-8")
print("patched")
