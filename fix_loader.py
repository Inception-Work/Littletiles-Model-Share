from pathlib import Path
p=Path("src/main/java/work/inception/ltsmodelshare/LtsModelShare.java"); s=p.read_text(encoding="utf-8")
if "MOD_LOADER" not in s:
    s=s.replace('public static final String GAME_VERSION = "1.20.1";','public static final String GAME_VERSION = "1.20.1";\n    public static final String MOD_LOADER = "neoforge";')
    p.write_text(s,encoding="utf-8")
q=Path("src/main/java/work/inception/ltsmodelshare/gui/GuiModels.java"); s=q.read_text(encoding="utf-8")
s=s.replace('query.sourceLoader = "forge";','query.sourceLoader = work.inception.ltsmodelshare.LtsModelShare.MOD_LOADER;')
q.write_text(s,encoding="utf-8")
print("ok")
