package work.inception.ltsmodelshare.server;

import com.creativemd.littletiles.common.util.grid.LittleGridContext;
import com.creativemd.littletiles.common.tile.preview.LittlePreviews;
import net.minecraft.nbt.*;
import net.minecraft.item.ItemStack;

/** Validate the actual downloaded structure, including nested child grids, before inventory mutation. */
public final class BlueprintValidation {
    private BlueprintValidation() { }
    public static final class UnsupportedGrid extends IllegalArgumentException {
        public final int needed;
        public UnsupportedGrid(int needed) { super("Unsupported LittleTiles grid: " + needed); this.needed=needed; }
    }
    public static void requireSupported(String snbt) throws NBTException {
        validateStructure(JsonToNBT.getTagFromJson(snbt), LittleGridContext.get().size, 0);
    }
    /** Legacy children inherit their parent's grid; root omission uses LittleTiles' configured default. */
    private static void validateStructure(NBTTagCompound tag, int inherited, int depth) {
        if(depth>128)throw new IllegalArgumentException("Structure nesting is too deep");
        if(tag.hasKey("grid")&&!tag.hasKey("grid",99))throw new IllegalArgumentException("Invalid grid declaration");
        int grid=tag.hasKey("grid")?tag.getInteger("grid"):inherited;
        try { if(LittleGridContext.get(grid)==null)throw new UnsupportedGrid(grid); }
        catch(RuntimeException invalid) {throw new UnsupportedGrid(grid);}
        if(tag.hasKey("children")) {
            NBTTagList children=tag.getTagList("children",10);
            for(int i=0;i<children.tagCount();i++)validateStructure(children.getCompoundTagAt(i),grid,depth+1);
        }
    }

    private static void validate(NBTBase value,int depth) {
        if(depth>128)throw new IllegalArgumentException("Structure nesting is too deep");
        if(value instanceof NBTTagCompound) {
            NBTTagCompound tag=(NBTTagCompound)value;
            for(String key:tag.getKeySet()) {
                NBTBase child=tag.getTag(key);
                if(("grid".equals(key)||"overallGrid".equals(key))&&child instanceof NBTPrimitive) {
                    int grid=((NBTPrimitive)child).getInt();
                    try { if(LittleGridContext.get(grid)==null)throw new UnsupportedGrid(grid); }
                    catch(RuntimeException invalid) {throw new UnsupportedGrid(grid);}
                }
                validate(child,depth+1);
            }
        } else if(value instanceof NBTTagList) {
            NBTTagList list=(NBTTagList)value;
            for(int i=0;i<list.tagCount();i++)validate(list.get(i),depth+1);
        }
    }
    public static void requireRenderable(ItemStack stack) {
        if(stack.getTagCompound()!=null)validate(stack.getTagCompound(),0);
        LittlePreviews.getPreview(stack,false);
    }
}
