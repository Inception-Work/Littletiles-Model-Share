package work.inception.ltsmodelshare.client;
import work.inception.ltsmodelshare.api.GridSupport;
/** Compatibility follows the actual configured LittleTiles slots and is never inferred from model dimensions. */
public final class GridLimits {
    private GridLimits() { }
    public static int maxGrid(){return GridSupport.maxGrid();}
    public static boolean exceeds(Integer grid){return grid!=null&&!GridSupport.supports(grid);}

    /** All plus the actual configured slots, sorted and deduplicated. */
    public static int[] filterOptions() {
        java.util.TreeSet<Integer> values=new java.util.TreeSet<Integer>();
        values.add(0);
        for (int i=0;i<team.creative.littletiles.common.grid.LittleGrid.gridCount();i++) { int count=team.creative.littletiles.common.grid.LittleGrid.gridByIndex(i).count; if(count>0 && count<=maxGrid()) values.add(count); }
        int[] result=new int[values.size()];int index=0;
        for(int value:values) result[index++]=value;
        return result;
    }
}
