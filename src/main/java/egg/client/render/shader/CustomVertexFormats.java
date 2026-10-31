package egg.client.render.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public final class CustomVertexFormats {
    public static final VertexFormat POSITION_COLOR_TEX_OVERLAY_LIGHT_NORMAL_LINE_WIDTH = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .add("UV2", VertexFormatElement.UV2)
        .add("Normal", VertexFormatElement.NORMAL)
        .padding(1)
        .add("LineWidth", VertexFormatElement.LINE_WIDTH)
        .build();

    public static final VertexFormat POSITION_TEX_COLOR_LIGHT_LINE_WIDTH = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV2", VertexFormatElement.UV2)
        .add("LineWidth", VertexFormatElement.LINE_WIDTH)
        .build();

    public static final VertexFormat POSITION_TEX_LINE_WIDTH = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("UV0", VertexFormatElement.UV0)
        .add("LineWidth", VertexFormatElement.LINE_WIDTH)
        .build();

    private CustomVertexFormats() {
    }
}
