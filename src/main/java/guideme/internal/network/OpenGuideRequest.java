package guideme.internal.network;

import guideme.PageAnchor;
import guideme.internal.GuideME;
import guideme.internal.GuideMEProxy;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public record OpenGuideRequest(Identifier guideId,
        Optional<PageAnchor> pageAnchor) implements CustomPacketPayload {

    public OpenGuideRequest(Identifier guideId) {
        this(guideId, Optional.empty());
    }

    public static final Type<OpenGuideRequest> TYPE = new Type<>(GuideME.makeId("open_guide"));

    public static final StreamCodec<FriendlyByteBuf, PageAnchor> ANCHOR_STREAM_CODEC = StreamCodec
            .of((buffer, value) -> {
                buffer.writeIdentifier(value.pageId());
                buffer.writeNullable(value.anchor(), ByteBufCodecs.STRING_UTF8);
            }, buffer -> {
                var page = buffer.readIdentifier();
                var fragment = buffer.readNullable(ByteBufCodecs.STRING_UTF8);
                return new PageAnchor(page, fragment);
            });

    public static final StreamCodec<FriendlyByteBuf, OpenGuideRequest> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, OpenGuideRequest::guideId,
            ByteBufCodecs.optional(ANCHOR_STREAM_CODEC), OpenGuideRequest::pageAnchor,
            OpenGuideRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles this request on the receiving (client) side. Called by the loader-specific packet handlers.
     */
    public void handle(Player player) {
        var anchor = pageAnchor().orElse(null);
        if (anchor != null) {
            GuideMEProxy.instance().openGuide(player, guideId(), anchor);
        } else {
            GuideMEProxy.instance().openGuide(player, guideId());
        }
    }
}
