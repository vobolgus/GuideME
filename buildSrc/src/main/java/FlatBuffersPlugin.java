import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

public class FlatBuffersPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply("java");

        // The flatbuffers schema and its generated code live in the repository root,
        // shared between the loader-specific projects (this plugin is applied to :neoforge).
        var rootLayout = project.getRootProject().getLayout().getProjectDirectory();
        var rootDir = project.getRootDir();

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava()
                .srcDir(new File(rootDir, "src/main/flatbuffers/generated"));

        var tasks = project.getTasks();
        var downloadFlatc = tasks.register("downloadFlatbufferCompiler", DownloadFlatBufferCompilerTask.class, task -> {
            task.getVersion().set(project.getProviders().gradleProperty("flatbuffers_version"));
        });

        /*
         * Update the generated Java Code for our scene export schema.
         * The code is checked in so this only needs to be run when the schema changes.
         */
        tasks.register("updateFlatbufferSources", GenerateFlatBufferCode.class, task -> {
            task.setGroup("build");
            task.getCompiler().set(downloadFlatc.flatMap(DownloadFlatBufferCompilerTask::getExecutableFile));
            task.getOutputDirectory().set(rootLayout.dir("src/main/flatbuffers/generated"));
            task.getSchemaFiles().from(new File(rootDir, "src/main/flatbuffers/scene.fbs"));
            task.getOptions().addAll(
                    "--gen-mutable",
                    "--java-package-prefix", "guideme.flatbuffers",
                    "--gen-generated",
                    "--java"
            );
            // sadly flatc uses an outdated annotation
            task.getReplacements().put("@javax.annotation.Generated", "@javax.annotation.processing.Generated");
        });

        /*
         * Generate the TypeScript sources for our schema. The sources are manually copied
         * over to the website repository.
         */
        tasks.register("updateFlatbufferTypescriptSources", GenerateFlatBufferCode.class, task -> {
            task.setGroup("build");
            task.getCompiler().set(downloadFlatc.flatMap(DownloadFlatBufferCompilerTask::getExecutableFile));
            task.getOutputDirectory().set(rootLayout.dir("web/scene-ts"));
            task.getSchemaFiles().from(new File(rootDir, "src/main/flatbuffers/scene.fbs"));
            task.getOptions().addAll(
                    "--ts-flat-files",
                    "--ts"
            );
        });

    }
}
