package de.frank.invoice.worker.cli;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parsed command line options for invoice processing.
 */
public record CliOptions(
        Path inputDirectory,
        Path configFile,
        OperatingProfile profile,
        boolean skipOcr,
        boolean mockText,
        boolean explicitSkipOcr,
        boolean explicitMockText) {

    private static final String PROCESS_COMMAND = "process";
    private static final String INPUT_OPTION = "--input";
    private static final String CONFIG_OPTION = "--config";
    private static final String PROFILE_OPTION = "--profile";
    private static final String SKIP_OCR_OPTION = "--skip-ocr";
    private static final String MOCK_TEXT_OPTION = "--mock-text";

    /**
     * Parses command line arguments.
     *
     * @param args command line arguments
     * @return parsed options
     */
    public static CliOptions parse(final String[] args) {
        final List<String> arguments = Arrays.asList(Objects.requireNonNull(args, "args must not be null"));
        if (arguments.isEmpty() || !PROCESS_COMMAND.equals(arguments.getFirst())) {
            throw new IllegalArgumentException("Unknown command.");
        }
        Path inputDirectory = null;
        Path configFile = null;
        OperatingProfile profile = OperatingProfile.DEFAULT;
        boolean skipOcr = false;
        boolean mockText = false;
        boolean explicitSkipOcr = false;
        boolean explicitMockText = false;

        int index = 1;
        while (index < arguments.size()) {
            final String argument = arguments.get(index);
            if (INPUT_OPTION.equals(argument)) {
                inputDirectory = Path.of(nextValue(arguments, index, INPUT_OPTION));
                index += 2;
            } else if (CONFIG_OPTION.equals(argument)) {
                configFile = Path.of(nextValue(arguments, index, CONFIG_OPTION));
                index += 2;
            } else if (PROFILE_OPTION.equals(argument)) {
                profile = OperatingProfile.parse(nextValue(arguments, index, PROFILE_OPTION));
                index += 2;
            } else if (SKIP_OCR_OPTION.equals(argument)) {
                skipOcr = true;
                explicitSkipOcr = true;
                index++;
            } else if (MOCK_TEXT_OPTION.equals(argument)) {
                mockText = true;
                explicitMockText = true;
                index++;
            } else {
                throw new IllegalArgumentException("Unknown option: " + argument);
            }
        }

        if (!explicitSkipOcr) {
            skipOcr = profile.skipOcr();
        }
        if (!explicitMockText) {
            mockText = profile.mockText();
        }
        return new CliOptions(inputDirectory, configFile, profile, skipOcr, mockText, explicitSkipOcr, explicitMockText);
    }

    /**
     * Checks whether the CLI arguments request local OCR skipping.
     *
     * @param args command line arguments
     * @return true if OCR should be skipped
     */
    public static boolean skipOcrRequested(final String[] args) {
        try {
            return parse(args).skipOcr();
        } catch (IllegalArgumentException exception) {
            return Arrays.asList(args).contains(SKIP_OCR_OPTION);
        }
    }

    /**
     * Checks whether the CLI arguments request deterministic mock PDF text.
     *
     * @param args command line arguments
     * @return true if mock text should be used
     */
    public static boolean mockTextRequested(final String[] args) {
        try {
            return parse(args).mockText();
        } catch (IllegalArgumentException exception) {
            return Arrays.asList(args).contains(MOCK_TEXT_OPTION);
        }
    }

    /**
     * Returns the optional config file.
     *
     * @return optional config file
     */
    public Optional<Path> optionalConfigFile() {
        return Optional.ofNullable(configFile);
    }

    private static String nextValue(final List<String> arguments, final int index, final String option) {
        if (index + 1 >= arguments.size()) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return arguments.get(index + 1);
    }
}
