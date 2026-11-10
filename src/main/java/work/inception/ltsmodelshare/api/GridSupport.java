package work.inception.ltsmodelshare.api;

import team.creative.littletiles.common.grid.LittleGrid;

/** Read the actual configured slots; some LittleTiles get(int) variants fall back instead of rejecting. */
public final class GridSupport {
    private GridSupport() { }
    public static boolean supports(int count) {
        for(int i=0;i<LittleGrid.gridCount();i++)if(LittleGrid.gridByIndex(i).count==count)return true;
        return false;
    }
    public static int maxGrid() { return LittleGrid.getMax().count; }
    public static int defaultGrid() { return LittleGrid.overallDefault().count; }
}
