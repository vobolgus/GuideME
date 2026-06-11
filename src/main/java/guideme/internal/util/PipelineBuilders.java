package guideme.internal.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Optional;

/**
 * Helper to derive a new {@link RenderPipeline} from an existing one. (NeoForge patches a {@code toBuilder()} method
 * into {@link RenderPipeline} for this purpose; this loader-neutral equivalent rebuilds a snippet from the public
 * getters instead.)
 */
public final class PipelineBuilders {
    private PipelineBuilders() {
    }

    public static RenderPipeline.Builder toBuilder(RenderPipeline pipeline) {
        var snippet = new RenderPipeline.Snippet(
                Optional.of(pipeline.getVertexShader()),
                Optional.of(pipeline.getFragmentShader()),
                Optional.of(pipeline.getShaderDefines()),
                Optional.of(pipeline.getSamplers()),
                Optional.of(pipeline.getUniforms()),
                Optional.of(pipeline.getColorTargetState()),
                Optional.ofNullable(pipeline.getDepthStencilState()),
                Optional.of(pipeline.getPolygonMode()),
                Optional.of(pipeline.isCull()),
                Optional.of(pipeline.getVertexFormat()),
                Optional.of(pipeline.getVertexFormatMode()));
        return RenderPipeline.builder(snippet);
    }
}
