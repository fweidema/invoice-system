package de.frank.invoice.worker.test.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Loads document scenarios from test resources.
 */
public class DocumentScenarioLoader {

    private static final String DEFAULT_SCENARIOS_RESOURCE = "scenarios";
    private static final String JSON_EXTENSION = ".json";

    private final ObjectMapper objectMapper;
    private final String scenariosResource;

    /**
     * Creates a scenario loader for the default scenarios resource directory.
     */
    public DocumentScenarioLoader() {
        this(DEFAULT_SCENARIOS_RESOURCE);
    }

    /**
     * Creates a scenario loader for an explicit scenarios resource directory.
     *
     * @param scenariosResource scenarios resource directory
     */
    public DocumentScenarioLoader(final String scenariosResource) {
        this.scenariosResource = Objects.requireNonNull(scenariosResource, "scenariosResource must not be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Loads all scenario JSON files from the configured resource directory.
     *
     * @return loaded scenarios sorted by scenario id
     */
    public List<DocumentScenario> loadScenarios() {
        final Path scenariosDirectory = scenariosDirectory();
        try (Stream<Path> paths = Files.walk(scenariosDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JSON_EXTENSION))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(this::readScenario)
                    .sorted(Comparator.comparing(DocumentScenario::id))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load document scenarios from: " + scenariosDirectory, exception);
        }
    }

    private Path scenariosDirectory() {
        final URL resource = Thread.currentThread().getContextClassLoader().getResource(scenariosResource);
        if (resource == null) {
            throw new IllegalStateException("Scenario resource directory not found: " + scenariosResource);
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid scenario resource URI: " + resource, exception);
        }
    }

    private DocumentScenario readScenario(final Path scenarioFile) {
        try {
            final JsonNode root = objectMapper.readTree(scenarioFile.toFile());
            final JsonNode expectedInvoice = root.required("expectedInvoice");
            return new DocumentScenario(
                    text(root, "id"),
                    text(root, "description"),
                    text(root, "document"),
                    new ExpectedInvoice(
                            text(expectedInvoice, "supplierName"),
                            text(expectedInvoice, "invoiceNumber"),
                            decimal(expectedInvoice, "grossAmount"),
                            date(expectedInvoice, "invoiceDate"),
                            text(expectedInvoice, "currency")));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read document scenario: " + scenarioFile, exception);
        }
    }

    private String text(final JsonNode node, final String fieldName) {
        return node.required(fieldName).asText();
    }

    private BigDecimal decimal(final JsonNode node, final String fieldName) {
        return node.required(fieldName).decimalValue();
    }

    private LocalDate date(final JsonNode node, final String fieldName) {
        return LocalDate.parse(text(node, fieldName));
    }
}