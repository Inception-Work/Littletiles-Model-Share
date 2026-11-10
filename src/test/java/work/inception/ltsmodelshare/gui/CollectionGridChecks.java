package work.inception.ltsmodelshare.gui;
import work.inception.ltsmodelshare.api.Dtos;
public final class CollectionGridChecks {
    public static void main(String[] args) {
        Dtos.Item a=new Dtos.Item(), b=new Dtos.Item(), unknown=new Dtos.Item();a.grid=32;b.grid=64;
        if(!CollectionGridFilter.matches(a,32)||CollectionGridFilter.matches(b,32)||CollectionGridFilter.matches(unknown,32))throw new AssertionError("Grid 32 filter");
        if(!CollectionGridFilter.matches(b,64)||CollectionGridFilter.matches(a,64))throw new AssertionError("Grid 64 filter");
        if(!CollectionGridFilter.matches(a,null)||!CollectionGridFilter.matches(b,null)||!CollectionGridFilter.matches(unknown,null))throw new AssertionError("All grids");
        if(CollectionGridFilter.matches(null,null))throw new AssertionError("Null entry");
        System.out.println("Mixed collection entry filtering checks passed: 9");
    }
}
