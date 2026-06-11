package guideme.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import guideme.Guides;
import guideme.internal.GuideMEClient;
import guideme.internal.GuideRegistry;
import guideme.internal.GuidebookText;
import guideme.internal.siteexport.ExportFeedbackSink;
import guideme.internal.siteexport.SiteExporter;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The client-side {@code /guidemec} command. Since the command source type for client commands differs between
 * loaders, registration is generic over the source type and loaders supply a {@link Feedback} adapter.
 */
public final class GuideClientCommand {
    private GuideClientCommand() {
    }

    /**
     * Adapts loader-specific client command sources for sending feedback.
     */
    public interface Feedback<S> {
        void sendFailure(S source, Component message);

        void sendSystemMessage(S source, Component message);
    }

    public static <S> void register(CommandDispatcher<S> dispatcher, Feedback<S> feedback) {
        var rootCommand = LiteralArgumentBuilder.<S>literal("guidemec");

        rootCommand.then(
                RequiredArgumentBuilder
                        .<S, net.minecraft.resources.Identifier>argument("guide", GuideIdArgument.argument())
                        .then(LiteralArgumentBuilder.<S>literal("export")
                                .executes(context -> {
                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                    var outputFolder = Minecraft.getInstance().gameDirectory.toPath()
                                            .resolve("guideme_exports").resolve(guideId.toDebugFileName());
                                    try {
                                        Files.createDirectories(outputFolder);
                                    } catch (IOException e) {
                                        feedback.sendFailure(context.getSource(), Component
                                                .literal("Failed to create output folder for export: "
                                                        + outputFolder));
                                        return 1;
                                    }

                                    var guide = GuideRegistry.getById(guideId);
                                    if (guide == null) {
                                        feedback.sendFailure(context.getSource(),
                                                Component.literal("Couldn't find guide " + guideId));
                                        return 1;
                                    }

                                    new SiteExporter(Minecraft.getInstance(), outputFolder, guide)
                                            .export(new ExportFeedbackSink() {
                                                @Override
                                                public void sendFeedback(Component message) {
                                                    feedback.sendSystemMessage(context.getSource(), message);
                                                }

                                                @Override
                                                public void sendError(Component message) {
                                                    feedback.sendFailure(context.getSource(), message);
                                                }
                                            });
                                    return 0;
                                }))
                        .then(LiteralArgumentBuilder.<S>literal("open")
                                .executes(context -> {
                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                    var guide = Guides.getById(guideId);
                                    if (guide == null) {
                                        feedback.sendFailure(context.getSource(),
                                                GuidebookText.ItemInvalidGuideId.text(guideId.toString()));
                                        return 1;
                                    }

                                    GuideMEClient.openGuideAtPreviousPage(guide, guide.getStartPage());
                                    return 0;
                                })
                                .then(
                                        RequiredArgumentBuilder
                                                .<S, guideme.PageAnchor>argument("page", PageAnchorArgument.argument())
                                                .executes(context -> {
                                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                                    var guide = Guides.getById(guideId);
                                                    var anchor = PageAnchorArgument.getPageAnchor(context, "page");
                                                    GuideMEClient.openGuideAtAnchor(guide, anchor);
                                                    return 0;
                                                }))

                        ));

        dispatcher.register(rootCommand);
    }
}
