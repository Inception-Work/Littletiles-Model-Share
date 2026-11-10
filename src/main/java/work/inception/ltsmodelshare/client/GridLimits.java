package work.inception.ltsmodelshare.client;
import work.inception.ltsmodelshare.api.GridSupport;
/** Compatibility follows the actual configured LittleTiles slots and is never inferred from model dimensions. */
public final class GridLimits {
    private GridLimits() { }
    public static int maxGrid(){return GridSupport.maxGrid();}
    public static boolean exceeds(Integer grid){return grid!=null&&!GridSupport.supports(grid);}
}
