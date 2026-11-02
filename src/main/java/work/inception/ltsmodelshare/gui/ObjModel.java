package work.inception.ltsmodelshare.gui;

import org.lwjgl.opengl.GL11;

import java.util.Arrays;

/** Bounded, untextured OBJ geometry. Parsing runs off-thread; GL calls run only on the client thread. */
final class ObjModel {
    // 8 MiB 的文本上限仍在下载层生效；使用原始数组避免装箱集合的额外开销。
    private static final int MAX_VERTICES = 500000;
    private static final int MAX_TRIANGLES = 500000;
    private final float[] positions;
    private final int[] indices;
    private final float cx, cy, cz;
    final double size;
    private int list;

    private ObjModel(float[] positions, int[] indices, float cx, float cy, float cz, double size) {
        this.positions = positions;
        this.indices = indices;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.size = size;
    }

    static ObjModel parse(String text) {
        if (text == null || text.isEmpty() || text.length() > 8 * 1024 * 1024)
            throw new IllegalArgumentException("OBJ exceeds size limit or is empty");
        float[] vertices = new float[3072];
        int[] triangles = new int[3072];
        int vertexCount = 0, triangleCount = 0;
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        // No regex split of the whole document: process one bounded line at a time.
        int cursor = 0;
        while (cursor < text.length()) {
            int end = text.indexOf('\n', cursor);
            if (end < 0) end = text.length();
            if (end - cursor > 16384) throw new IllegalArgumentException("OBJ line too long");
            String line = text.substring(cursor, end).trim();
            cursor = end + 1;
            if (line.startsWith("v ") || line.startsWith("v\t")) {
                if (vertexCount >= MAX_VERTICES) throw new IllegalArgumentException("OBJ exceeds " + MAX_VERTICES + " vertices");
                String[] parts = line.split("\\s+");
                if (parts.length < 4) throw new IllegalArgumentException("Invalid OBJ vertex");
                float x = Float.parseFloat(parts[1]), y = Float.parseFloat(parts[2]), z = Float.parseFloat(parts[3]);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
                    throw new IllegalArgumentException("Non-finite OBJ vertex");
                if (vertices.length < (vertexCount + 1) * 3)
                    vertices = Arrays.copyOf(vertices, Math.min(MAX_VERTICES * 3, vertices.length * 2));
                int offset = vertexCount++ * 3;
                vertices[offset] = x; vertices[offset + 1] = y; vertices[offset + 2] = z;
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            } else if (line.startsWith("f ") || line.startsWith("f\t")) {
                String[] parts = line.split("\\s+");
                if (parts.length < 4 || parts.length > 257) throw new IllegalArgumentException("Invalid OBJ face");
                int first = index(parts[1], vertexCount);
                int previous = index(parts[2], vertexCount);
                for (int i = 3; i < parts.length; i++) {
                    if (triangleCount >= MAX_TRIANGLES) throw new IllegalArgumentException("OBJ exceeds " + MAX_TRIANGLES + " triangles");
                    int next = index(parts[i], vertexCount);
                    if (triangles.length < (triangleCount + 1) * 3)
                        triangles = Arrays.copyOf(triangles, Math.min(MAX_TRIANGLES * 3, triangles.length * 2));
                    int offset = triangleCount++ * 3;
                    triangles[offset] = first; triangles[offset + 1] = previous; triangles[offset + 2] = next;
                    previous = next;
                }
            }
        }
        if (triangleCount == 0) throw new IllegalArgumentException("OBJ contains no faces");
        float[] points = Arrays.copyOf(vertices, vertexCount * 3);
        int[] faces = Arrays.copyOf(triangles, triangleCount * 3);
        double extent = Math.max(maxX - (double) minX, Math.max(maxY - (double) minY, maxZ - (double) minZ));
        return new ObjModel(points, faces, (float) (minX / 2D + maxX / 2D),
                (float) (minY / 2D + maxY / 2D), (float) (minZ / 2D + maxZ / 2D),
                Math.max(0.01, extent));
    }

    private static int index(String token, int count) {
        String value = token.split("/", -1)[0];
        int raw = Integer.parseInt(value);
        int result = raw > 0 ? raw - 1 : count + raw;
        if (raw == 0 || result < 0 || result >= count) throw new IllegalArgumentException("Invalid OBJ index");
        return result;
    }

    void render() {
        if (list == 0) {
            list = GL11.glGenLists(1);
            if (list == 0) throw new IllegalStateException("Cannot allocate OBJ display list");
            GL11.glNewList(list, GL11.GL_COMPILE);
            try {
                GL11.glBegin(GL11.GL_TRIANGLES);
                for (int i = 0; i < indices.length; i += 3) {
                    int a = indices[i] * 3, b = indices[i + 1] * 3, c = indices[i + 2] * 3;
                    float ux = positions[b] - positions[a], uy = positions[b + 1] - positions[a + 1], uz = positions[b + 2] - positions[a + 2];
                    float vx = positions[c] - positions[a], vy = positions[c + 1] - positions[a + 1], vz = positions[c + 2] - positions[a + 2];
                    float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    double length = Math.sqrt((double) nx * nx + (double) ny * ny + (double) nz * nz);
                    float shade = length > 0 ? (float) (0.65 + 0.27 * Math.abs(ny / length)) : 0.8f;
                    GL11.glColor3f(shade, shade, shade);
                    GL11.glVertex3f(positions[a] - cx, positions[a + 1] - cy, positions[a + 2] - cz);
                    GL11.glVertex3f(positions[b] - cx, positions[b + 1] - cy, positions[b + 2] - cz);
                    GL11.glVertex3f(positions[c] - cx, positions[c + 1] - cy, positions[c + 2] - cz);
                }
                GL11.glEnd();
            } finally {
                GL11.glEndList();
            }
        }
        GL11.glCallList(list);
    }

    void release() {
        if (list != 0) {
            GL11.glDeleteLists(list, 1);
            list = 0;
        }
    }
}
