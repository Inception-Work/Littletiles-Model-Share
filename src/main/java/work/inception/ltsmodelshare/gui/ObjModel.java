package work.inception.ltsmodelshare.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Bounded, untextured OBJ geometry for the in-game white-model preview. */
final class ObjModel {
    private static final int MAX_VERTICES = 500_000;
    private static final int MAX_TRIANGLES = 500_000;
    private final float[] positions;
    private final int[] indices;
    final double size;

    private ObjModel(float[] positions, int[] indices, double size) {
        this.positions = positions;
        this.indices = indices;
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
                    vertices = java.util.Arrays.copyOf(vertices, Math.min(MAX_VERTICES * 3, vertices.length * 2));
                int offset = vertexCount++ * 3;
                vertices[offset] = x;
                vertices[offset + 1] = y;
                vertices[offset + 2] = z;
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
                        triangles = java.util.Arrays.copyOf(triangles, Math.min(MAX_TRIANGLES * 3, triangles.length * 2));
                    int offset = triangleCount++ * 3;
                    triangles[offset] = first;
                    triangles[offset + 1] = previous;
                    triangles[offset + 2] = next;
                    previous = next;
                }
            }
        }
        if (triangleCount == 0) throw new IllegalArgumentException("OBJ contains no faces");
        float[] points = java.util.Arrays.copyOf(vertices, vertexCount * 3);
        int[] faces = java.util.Arrays.copyOf(triangles, triangleCount * 3);
        double extent = Math.max(maxX - (double) minX, Math.max(maxY - (double) minY, maxZ - (double) minZ));
        float cx = (float) (minX / 2D + maxX / 2D);
        float cy = (float) (minY / 2D + maxY / 2D);
        float cz = (float) (minZ / 2D + maxZ / 2D);
        for (int i = 0; i < points.length; i += 3) {
            points[i] -= cx;
            points[i + 1] -= cy;
            points[i + 2] -= cz;
        }
        return new ObjModel(points, faces, Math.max(0.01, extent));
    }

    private static int index(String token, int count) {
        String value = token.split("/", -1)[0];
        int raw = Integer.parseInt(value);
        int result = raw > 0 ? raw - 1 : count + raw;
        if (raw == 0 || result < 0 || result >= count) throw new IllegalArgumentException("Invalid OBJ index");
        return result;
    }

    /** Draw centered at the current pose, normalized so the longest side is 1 unit. */
    void render(PoseStack pose) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        Matrix4f matrix = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float scale = (float) (1.0 / size);
        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i] * 3, b = indices[i + 1] * 3, c = indices[i + 2] * 3;
            float ux = positions[b] - positions[a], uy = positions[b + 1] - positions[a + 1], uz = positions[b + 2] - positions[a + 2];
            float vx = positions[c] - positions[a], vy = positions[c + 1] - positions[a + 1], vz = positions[c + 2] - positions[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            double length = Math.sqrt((double) nx * nx + (double) ny * ny + (double) nz * nz);
            float shade = length > 0 ? (float) (0.65 + 0.27 * Math.abs(ny / length)) : 0.8f;
            vertex(buffer, matrix, positions[a] * scale, positions[a + 1] * scale, positions[a + 2] * scale, shade);
            vertex(buffer, matrix, positions[b] * scale, positions[b + 1] * scale, positions[b + 2] * scale, shade);
            vertex(buffer, matrix, positions[c] * scale, positions[c + 1] * scale, positions[c + 2] * scale, shade);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float shade) {
        buffer.addVertex(matrix, x, y, z).setColor(shade, shade, shade, 1f);
    }
}
