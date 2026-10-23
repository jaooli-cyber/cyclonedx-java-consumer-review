import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyclonedx.Version;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.BomParserFactory;
import org.dependencytrack.parser.cyclonedx.util.ModelConverter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/** Runs the real pinned consumer converter, not a reimplementation or database mock. */
class ConsumerBoundaryCheck {
    public static void main(String[] args) throws Exception {
        String library = args[0];
        Path inputs = Path.of(args[1]);
        Path output = Path.of(args[2]);
        Files.createDirectories(output);
        var mapper = new ObjectMapper();
        var matrix = mapper.readTree(inputs.resolve("fixture-matrix.json").toFile());
        var records = mapper.createArrayNode();
        String coreOrigin = Path.of(Bom.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString();
        if (!coreOrigin.equals("core-" + library + ".jar")) throw new IllegalStateException("unexpected core library: " + coreOrigin);
        boolean hasCryptoAccessor = Arrays.stream(org.dependencytrack.model.Component.class.getMethods())
                .anyMatch(m -> m.getName().equals("getCryptoProperties"));
        int consumedFiles = 0;
        int components = 0;
        int cryptoLost = 0;
        int unexpectedFailures = 0;
        for (var test : matrix.path("cases")) {
            String filename = test.path("file").asText();
            var row = mapper.createObjectNode();
            row.put("file", filename);
            row.put("libraryVersion", library);
            byte[] raw = Files.readAllBytes(inputs.resolve("fixtures").resolve(filename));
            Bom bom;
            try {
                bom = BomParserFactory.createParser(raw).parse(raw);
                row.put("parsed", true);
            } catch (Exception error) {
                row.put("parsed", false);
                row.put("errorClass", error.getClass().getName());
                if (test.path(library.equals("13.2.0") ? "oldParseExpected" : "newParseExpected").asBoolean()) {
                    unexpectedFailures++;
                }
                records.add(row);
                continue;
            }
            consumedFiles++;
            if (!test.path(library.equals("13.2.0") ? "oldParseExpected" : "newParseExpected").asBoolean()) unexpectedFailures++;
            var converted = mapper.createArrayNode();
            var exported = new ArrayList<org.cyclonedx.model.Component>();
            for (var cdx : bom.getComponents()) {
                var internal = ModelConverter.convertComponent(cdx);
                // The conversion test deliberately does not claim persistence.
                // Set a deterministic identity as the normal persistence layer would.
                internal.setUuid(UUID.nameUUIDFromBytes(cdx.getBomRef().getBytes(StandardCharsets.UTF_8)));
                var returned = ModelConverter.convert(internal);
                var value = mapper.createObjectNode();
                value.put("sourceBomRef", cdx.getBomRef());
                value.put("internalBomRef", internal.getBomRef());
                value.put("inputType", cdx.getType().name());
                value.put("internalClassifier", internal.getClassifier().name());
                value.put("outputType", returned.getType().name());
                value.put("namePreserved", cdx.getName().equals(returned.getName()));
                value.put("cryptoPropertiesPresentBefore", cdx.getCryptoProperties() != null);
                value.put("cryptoPropertiesPresentAfter", returned.getCryptoProperties() != null);
                if (cdx.getCryptoProperties() != null && returned.getCryptoProperties() == null) cryptoLost++;
                if (!value.path("namePreserved").asBoolean() || !cdx.getType().equals(returned.getType())) unexpectedFailures++;
                converted.add(value);
                exported.add(returned);
                components++;
            }
            var result = new Bom();
            result.setComponents(exported);
            String json = BomGeneratorFactory.createJson(Version.VERSION_17, result).toJsonString();
            Files.writeString(output.resolve(filename + ".consumer-export.json"), json);
            row.set("convertedComponents", converted);
            records.add(row);
        }
        var result = mapper.createObjectNode();
        result.put("status", unexpectedFailures == 0 ? "COMPLETED_WITH_MEASURED_RETENTION" : "UNEXPECTED_FAILURE");
        result.put("libraryVersion", library);
        result.put("actualCoreArtifact", coreOrigin);
        result.put("actualSchemaValidatorArtifact", Path.of(Class.forName("com.networknt.schema.Schema")
                .getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString());
        result.put("consumerVariant", library.equals("13.2.0") ? "PINNED_BASE" : "PR_WITH_LOCAL_VALIDATOR_2_0_8_OVERRIDE");
        result.put("inputFiles", records.size());
        result.put("parsedAndConvertedFiles", consumedFiles);
        result.put("convertedComponents", components);
        result.put("componentsLosingCryptoProperties", cryptoLost);
        result.put("internalModelHasCryptoPropertiesAccessor", hasCryptoAccessor);
        result.put("unexpectedFailures", unexpectedFailures);
        result.put("realConsumerConverterExecuted", true);
        result.put("databasePersistenceTested", false);
        result.put("httpImportTested", false);
        result.put("uiTested", false);
        result.set("records", records);
        Files.writeString(output.resolve("results.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
        System.out.println(library + ": convertedFiles=" + consumedFiles + ", components=" + components + ", cryptoPropertiesMissingAfter=" + cryptoLost);
        if (unexpectedFailures > 0) System.exit(1);
    }
}
