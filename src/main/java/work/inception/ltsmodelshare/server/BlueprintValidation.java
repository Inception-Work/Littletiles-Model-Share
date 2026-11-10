package work.inception.ltsmodelshare.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import work.inception.ltsmodelshare.api.GridSupport;

/** Modern LittleTiles gives each level its own default grid, rather than legacy parent inheritance. */
public final class BlueprintValidation {
    private BlueprintValidation() { }
    public static void requireSupported(String snbt) throws Exception { requireSupported(TagParser.parseTag(snbt)); }
    public static void requireSupported(CompoundTag tag) { validate(tag,0); }
    private static void validate(CompoundTag tag,int depth) {
        if(depth>128)throw new IllegalArgumentException("Structure nesting is too deep");
        if(tag.contains("grid")&&!tag.contains("grid",Tag.TAG_ANY_NUMERIC))throw new IllegalArgumentException("Invalid grid declaration");
        int grid=tag.contains("grid")?tag.getInt("grid"):GridSupport.defaultGrid();
        if(!GridSupport.supports(grid))throw new IllegalArgumentException("invalid_grid: unsupported LittleTiles grid: "+grid+"; configured maximum: "+GridSupport.maxGrid());
        for(String key:new String[]{"c","children"}) {
            if(!tag.contains(key))continue;
            ListTag children=tag.getList(key,Tag.TAG_COMPOUND);
            for(int i=0;i<children.size();i++)validate(children.getCompound(i),depth+1);
        }
    }
}
