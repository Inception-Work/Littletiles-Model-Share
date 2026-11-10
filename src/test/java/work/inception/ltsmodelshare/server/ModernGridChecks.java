package work.inception.ltsmodelshare.server;

import team.creative.littletiles.common.grid.LittleGrid;
import work.inception.ltsmodelshare.api.GridSupport;
import work.inception.ltsmodelshare.client.GridLimits;

public final class ModernGridChecks {
    private static int checks;
    private static void check(boolean condition) {checks++;if(!condition)throw new AssertionError("Grid check "+checks);}
    private static void reject(String snbt) throws Exception {
        try {BlueprintValidation.requireSupported(snbt);throw new AssertionError("Unsupported structure accepted");}
        catch(IllegalArgumentException expected) {checks++;}
    }
    public static void main(String[] args) throws Exception {
        LittleGrid.loadGrid(1,8,2,16);
        BlueprintValidation.requireSupported("{grid:128,c:[{grid:64}]}");checks++;
        check(java.util.Arrays.stream(GridLimits.filterOptions()).max().getAsInt()==128);
        LittleGrid.loadGrid(1,6,2,16);
        check(GridSupport.maxGrid()==32);
        check(java.util.Arrays.stream(GridLimits.filterOptions()).max().getAsInt()==32);
        check(GridSupport.supports(32));check(!GridSupport.supports(64));check(!GridSupport.supports(24));
        check(GridLimits.exceeds(64));check(!GridLimits.exceeds(32));
        reject("{grid:64}");reject("{grid:16,c:[{grid:64}]}");reject("{grid:16,c:[{grid:16,c:[{grid:128}]}]}");
        reject("{grid:24}");reject("{grid:0}");reject("{grid:\"big\"}");
        BlueprintValidation.requireSupported("{c:[{}, {grid:32}]}");checks++;
        BlueprintValidation.requireSupported("{grid:16,t:[{b:[I;0,0,0,128,128,128]}]}");checks++;
        System.out.println("Modern configured-grid and nested-structure checks passed: "+checks);
    }
}
