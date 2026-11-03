from pathlib import Path
p=Path("build.gradle"); s=p.read_text(encoding="utf-8")
needle="jvmArguments.addAll(['-Xms1G', '-Xmx6G', '-XX:+UseG1GC'])"
add=(needle + "\n        jvmArguments.addAll(['-Dmixin.env.remapRefMap=true', "
     "'-Dmixin.env.refMapRemappingFile=D:/DevelopmentProject/littletiles-model-share/build/createSrgToMcp/output.srg'])")
if "mixin.env.remapRefMap" not in s:
    s=s.replace(needle, add)
    p.write_text(s,encoding="utf-8")
print("ok")
