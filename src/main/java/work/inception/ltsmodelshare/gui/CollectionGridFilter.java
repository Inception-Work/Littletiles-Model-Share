package work.inception.ltsmodelshare.gui;
import work.inception.ltsmodelshare.api.Dtos;
/** Explicit grid selections apply to individual collection entries, independently of runtime download limits. */
public final class CollectionGridFilter {
    private CollectionGridFilter() { }
    public static boolean matches(Dtos.Item item, Integer selectedGrid) {
        return item != null && (selectedGrid == null || selectedGrid.equals(item.grid));
    }
}
