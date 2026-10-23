import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyclonedx.Version;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.BomParserFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Actual released library calls; assertions compare predeclared input projections. */
class EnumRoundTripCheck {
    static final ObjectMapper mapper = new ObjectMapper();
    static ArrayNode projection(JsonNode components) {
        var rows = new ArrayList<JsonNode>();
        for (var c : components) {
            var row = mapper.createObjectNode();
            for (String key : List.of("bom-ref", "name", "type", "cryptoProperties")) {
                if (c.has(key)) row.set(key, c.get(key));
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(c -> c.path("bom-ref").asText()));
        var result = mapper.createArrayNode();
        rows.forEach(result::add);
        return result;
    }
    static ArrayNode projection(Bom bom) throws Exception {
        var json = BomGeneratorFactory.createJson(Version.VERSION_17, bom).toJsonString();
        return projection(mapper.readTree(json).path("components"));
    }
    public static void main(String[] args) throws Exception {
        String version = args[0];
        Path inputs = Path.of(args[1]);
        Path destination = Path.of(args[2]);
        Files.createDirectories(destination);
        var matrix = mapper.readTree(inputs.resolve("fixture-matrix.json").toFile());
        var records = mapper.createArrayNode();
        int failures = 0;
        int roundTrips = 0;
        for (var test : matrix.path("cases")) {
            String filename = test.path("file").asText();
            byte[] raw = Files.readAllBytes(inputs.resolve("fixtures").resolve(filename));
            var row = mapper.createObjectNode();
            row.put("file", filename);
            row.put("libraryVersion", version);
            row.put("parseExpected", test.path(version.equals("13.2.0") ? "oldParseExpected" : "newParseExpected").asBoolean());
            var parser = BomParserFactory.createParser(raw);
            var diagnostics = parser.validate(raw, Version.VERSION_17);
            boolean valid = diagnostics.isEmpty();
            row.put("schemaValid", valid);
            row.put("schemaMatchesExpectation", valid == test.path("schemaValid").asBoolean());
            row.put("schemaDiagnosticCount", diagnostics.size());
            Bom bom = null;
            try {
                bom = parser.parse(raw);
                row.put("parsed", true);
            } catch (Exception error) {
                row.put("parsed", false);
                row.put("errorClass", error.getClass().getName());
                String message = error.getMessage();
                row.put("error", message == null ? "" : message.substring(0, Math.min(800, message.length())));
            }
            boolean matches = row.path("parsed").asBoolean() == row.path("parseExpected").asBoolean();
            row.put("parseMatchesExpectation", matches);
            if (!matches || !row.path("schemaMatchesExpectation").asBoolean()) failures++;
            if (bom != null) {
                var expected = projection(test.path("expectedProjection"));
                var actual = projection(bom);
                row.set("parsedProjection", actual);
                row.put("parsedProjectionPreserved", actual.equals(expected));
                if (!actual.equals(expected)) failures++;
                var formats = mapper.createObjectNode();
                for (String format : List.of("json", "xml")) {
                    String output = format.equals("json")
                            ? BomGeneratorFactory.createJson(Version.VERSION_17, bom).toJsonString()
                            : BomGeneratorFactory.createXml(Version.VERSION_17, bom).toXmlString();
                    byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
                    Files.write(destination.resolve(filename + ".roundtrip." + format), bytes);
                    var outputParser = BomParserFactory.createParser(bytes);
                    boolean outputValid = outputParser.validate(bytes, Version.VERSION_17).isEmpty();
                    var again = outputParser.parse(bytes);
                    boolean preserved = projection(again).equals(expected);
                    var checked = mapper.createObjectNode();
                    checked.put("schemaValid", outputValid);
                    checked.put("projectionPreserved", preserved);
                    formats.set(format, checked);
                    if (!outputValid || !preserved) failures++;
                    roundTrips++;
                }
                row.set("roundTrips", formats);
            }
            records.add(row);
        }
        var result = mapper.createObjectNode();
        result.put("status", failures == 0 ? "PASS" : "EXPECTATION_MISMATCH");
        result.put("libraryVersion", version);
        result.put("inputFiles", records.size());
        result.put("roundTrips", roundTrips);
        result.put("expectationMismatches", failures);
        result.set("records", records);
        Files.writeString(destination.resolve("results.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
        System.out.println(version + ": files=" + records.size() + ", roundTrips=" + roundTrips + ", mismatches=" + failures);
        if (failures != 0) System.exit(1);
    }
}
