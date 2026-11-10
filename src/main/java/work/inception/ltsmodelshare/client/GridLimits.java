package work.inception.ltsmodelshare.client;

import static work.inception.ltsmodelshare.client.Translations.tr;

import com.creativemd.littletiles.common.util.grid.LittleGridContext;
import work.inception.ltsmodelshare.LtsModelShare;

/**
 * 读 LittleTiles 当前允许的 grid 上限。
 *
 * <p>LittleTiles 的 grid 档位是 <b>minSize × exponent^i</b>（i = 0 … scale-1）：</p>
 * <pre>
 * config/littletiles.json
 *   "core": { "minSize": 1, "exponent": 2, "scale": 6 }   →  1, 2, 4, 8, 16, 32
 * </pre>
 * <p>所以默认上限是 <b>32</b>。想要更大的 grid（比如站点上 grid 48 的结构），
 * 把 <code>scale</code> 从 6 改成 7（上限 64）或 8（128），然后重启游戏。</p>
 */
public final class GridLimits {

    private static int cachedMax = -1;

    private GridLimits() {
    }

    /** 客户端当前支持的最大 grid；拿不到返回 0（表示"无法判断，不做拦截"） */
    public static int maxGrid() {
        try {
            LittleGridContext max = LittleGridContext.getMax();
            cachedMax = max == null ? 0 : max.size;
        } catch (Throwable t) {
            LtsModelShare.LOG.debug("读取 LittleTiles grid 上限失败：{}", t.toString());
            cachedMax = 0;
        }
        return cachedMax;
    }

    /** 结构用的 grid 是否超出客户端上限 */
    public static boolean exceeds(Integer grid) {
        if(grid==null)return false; // Unknown metadata is checked against the actual file before import.
        try { return LittleGridContext.get(grid)==null; }
        catch(RuntimeException unsupported) { return true; }
    }

    /** 给玩家看的「怎么改配置」的提示 */
    public static String fixHint(int needed) {
        int max = maxGrid();
        // 每多一层 scale，上限翻 exponent 倍（默认 2 倍）
        int scale = 6;
        int target = max > 0 ? max : 32;
        while (target < needed && scale < 12) {
            scale++;
            target *= 2;
        }
        return tr("ltsmodelshare.grid.hint", scale, target);
    }
    /** 要支持 grid=needed，建议把 core.scale 改成几（默认 6 → 上限 32，每 +1 翻倍） */
    public static int suggestScale(int needed) {
        int max = maxGrid() > 0 ? maxGrid() : 32;
        int scale = 6;
        while (max < needed && scale < 12) {
            scale++;
            max *= 2;
        }
        return scale;
    }

    /** 按建议的 scale 算上限，用来在提示里告诉玩家改完能到多少 */
    public static int maxForScale(int scale) {
        return (int) Math.pow(2, scale - 1);
    }
}
