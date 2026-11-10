package work.inception.ltsmodelshare.server;
import com.creativemd.littletiles.common.util.grid.LittleGridContext;
public final class BlueprintGridChecks {
    private static void reject(String snbt,int grid) throws Exception {
        try {BlueprintValidation.requireSupported(snbt);throw new AssertionError("Unsupported grid accepted");}
        catch(BlueprintValidation.UnsupportedGrid expected) {if(expected.needed!=grid)throw new AssertionError("Wrong grid");}
    }
    public static void main(String[] args) throws Exception {
        LittleGridContext.loadGrid(1,16,6,2);
        if(java.util.Arrays.stream(work.inception.ltsmodelshare.client.GridLimits.filterOptions()).max().getAsInt()!=32)throw new AssertionError("Grid cycle ignores config");
        BlueprintValidation.requireSupported("{grid:16,tiles:[],children:[{grid:32,tiles:[]}]}");
        BlueprintValidation.requireSupported("{tiles:[],children:[{tiles:[]}]}");
        BlueprintValidation.requireSupported("{grid:32,tiles:[],children:[{tiles:[]}]}");
        BlueprintValidation.requireSupported("{tiles:[{boxes:[[I;0,0,0,128,128,128]]}]}");
        reject("{grid:128,children:[{tiles:[]}]}",128);
        if(!work.inception.ltsmodelshare.client.GridLimits.exceeds(24))throw new AssertionError("Unsupported slot accepted");
        if(work.inception.ltsmodelshare.client.GridLimits.exceeds(32))throw new AssertionError("Configured slot rejected");
        reject("{grid:128,tiles:[]}",128);
        reject("{grid:16,children:[{grid:64,tiles:[]}]}",64);
        reject("{grid:16,children:[{grid:16,children:[{grid:128}]}]}",128);
        reject("{grid:24,tiles:[]}",24);
        reject("{grid:0,tiles:[]}",0);
        LittleGridContext.loadGrid(1,16,8,2);
        BlueprintValidation.requireSupported("{grid:128,children:[{grid:64}]}");
        reject("{grid:256}",256);
        System.out.println("Actual and nested blueprint grid checks passed: 14");
    }
}
