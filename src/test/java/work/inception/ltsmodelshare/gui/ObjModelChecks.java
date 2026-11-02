package work.inception.ltsmodelshare.gui;

/** Headless regression for large, generated OBJ meshes; never invokes OpenGL. */
public final class ObjModelChecks {
    public static void main(String[] args) {
        StringBuilder text = new StringBuilder("v 0 0 0\nv 1 0 0\nv 0 1 0\n");
        for (int i = 0; i < 120001; i++) text.append("f -3 -2 -1\n");
        if (ObjModel.parse(text.toString()).size < 1) throw new AssertionError("Large face mesh lost");

        text.setLength(0);
        for (int i = 0; i < 150001; i++) text.append("v 0 0 0\n");
        text.append("f 1 2 3\n");
        ObjModel.parse(text.toString());

        try {
            ObjModel.parse("v 0 0 0\nf 1 2 3\n");
            throw new AssertionError("Malformed indices were accepted");
        } catch (IllegalArgumentException expected) {
            // Invalid geometry must not reach the render thread.
        }
        System.out.println("Large OBJ parsing and malformed-index checks passed");
    }
}
