from pathlib import Path
p=Path("build.gradle"); s=p.read_text(encoding="utf-8").replace("version = '0.1.6'","version = '0.1.7'"); p.write_text(s,encoding="utf-8")
g=Path("gradle.properties"); s=g.read_text(encoding="utf-8").replace("modVersion=0.1.6","modVersion=0.1.7"); g.write_text(s,encoding="utf-8")
print("bumped")
