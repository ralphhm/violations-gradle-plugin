package se.bjurr.violations.gradle.plugin;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static se.bjurr.violations.git.ViolationsReporterApi.violationsReporterApi;
import static se.bjurr.violations.git.ViolationsReporterDetailLevel.VERBOSE;
import static se.bjurr.violations.lib.ViolationsApi.violationsApi;
import static se.bjurr.violations.lib.model.SEVERITY.INFO;
import static se.bjurr.violations.lib.model.codeclimate.CodeClimateTransformer.fromViolations;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.script.ScriptException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import se.bjurr.violations.git.ViolationsGit;
import se.bjurr.violations.git.ViolationsReporterDetailLevel;
import se.bjurr.violations.lib.model.SEVERITY;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.reports.Parser;
import se.bjurr.violations.lib.util.Filtering;
import se.bjurr.violations.violationslib.com.google.gson.GsonBuilder;

public class ViolationsTask extends DefaultTask {

  private List<List<String>> violations = new ArrayList<>();
  private SEVERITY minSeverity = INFO;
  private ViolationsReporterDetailLevel detailLevel = VERBOSE;
  private Integer maxViolations = Integer.MAX_VALUE;
  private boolean printViolations;
  private String diffFrom;
  private String diffTo;
  private SEVERITY diffMinSeverity = INFO;
  private File gitRepo = new File(".");
  private boolean diffPrintViolations;
  private Integer diffMaxViolations = Integer.MAX_VALUE;
  private ViolationsReporterDetailLevel diffDetailLevel = VERBOSE;
  private int maxReporterColumnWidth;
  private int maxRuleColumnWidth = 10;
  private int maxSeverityColumnWidth;
  private int maxLineColumnWidth;
  private int maxMessageColumnWidth = 50;
  private File codeClimateFile;
  private File violationsFile;

  public void setCodeClimateFile(final File codeClimateFile) {
    this.codeClimateFile = codeClimateFile;
  }

  public void setViolationsFile(final File violationsFile) {
    this.violationsFile = violationsFile;
  }

  public void setMinSeverity(final SEVERITY minSeverity) {
    this.minSeverity = minSeverity;
  }

  public void setViolations(final List<List<String>> violations) {
    this.violations = violations;
  }

  public void setDetailLevel(final ViolationsReporterDetailLevel detailLevel) {
    this.detailLevel = detailLevel;
  }

  public void setMaxViolations(final Integer maxViolations) {
    this.maxViolations = maxViolations;
  }

  public void setDiffDetailLevel(final ViolationsReporterDetailLevel diffDetailLevel) {
    this.diffDetailLevel = diffDetailLevel;
  }

  public void setDiffFrom(final String diffFrom) {
    this.diffFrom = diffFrom;
  }

  public void setDiffMaxViolations(final Integer diffMaxViolations) {
    this.diffMaxViolations = diffMaxViolations;
  }

  public void setDiffMinSeverity(final SEVERITY diffMinSeverity) {
    this.diffMinSeverity = diffMinSeverity;
  }

  public void setDiffTo(final String diffTo) {
    this.diffTo = diffTo;
  }

  public void setGitRepo(final File gitRepo) {
    this.gitRepo = gitRepo;
  }

  public void setDiffPrintViolations(final boolean diffPrintViolations) {
    this.diffPrintViolations = diffPrintViolations;
  }

  public void setPrintViolations(final boolean printViolations) {
    this.printViolations = printViolations;
  }

  public void setMaxLineColumnWidth(final int maxLineColumnWidth) {
    this.maxLineColumnWidth = maxLineColumnWidth;
  }

  public void setMaxMessageColumnWidth(final int maxMessageColumnWidth) {
    this.maxMessageColumnWidth = maxMessageColumnWidth;
  }

  public void setMaxReporterColumnWidth(final int maxReporterColumnWidth) {
    this.maxReporterColumnWidth = maxReporterColumnWidth;
  }

  public void setMaxRuleColumnWidth(final int maxRuleColumnWidth) {
    this.maxRuleColumnWidth = maxRuleColumnWidth;
  }

  public void setMaxSeverityColumnWidth(final int maxSeverityColumnWidth) {
    this.maxSeverityColumnWidth = maxSeverityColumnWidth;
  }

  @TaskAction
  public void gitChangelogPluginTasks() throws Exception {
    final List<Violation> allParsedViolations = new ArrayList<>();
    final List<Violation> allParsedViolationsInDiff = new ArrayList<>();
    for (final List<String> configuredViolation : violations) {
      final List<Violation> parsedViolations = getAllParsedViolations(configuredViolation);

      allParsedViolations.addAll(getFiltered(parsedViolations, minSeverity));

      allParsedViolationsInDiff.addAll(getAllViolationsInDiff(parsedViolations));
    }

    if (this.codeClimateFile != null) {
      createJsonFile(fromViolations(allParsedViolations), this.codeClimateFile);
    }
    if (this.violationsFile != null) {
      createJsonFile(allParsedViolations, this.violationsFile);
    }
    checkGlobalViolations(allParsedViolations);
    checkDiffViolations(allParsedViolationsInDiff);
  }

  private void createJsonFile(final Object object, final File file) throws IOException {
    final String codeClimateReport = new GsonBuilder().setPrettyPrinting().create().toJson(object);
    Files.write(
        file.toPath(),
        codeClimateReport.getBytes(StandardCharsets.UTF_8),
        TRUNCATE_EXISTING,
        CREATE,
        WRITE);
  }

  private void checkGlobalViolations(final List<Violation> violations) throws ScriptException {
    final boolean tooManyViolations = violations.size() > maxViolations;
    if (!tooManyViolations && !printViolations) {
      return;
    }

    final String report =
        violationsReporterApi() //
            .withViolations(violations) //
            .withMaxLineColumnWidth(maxLineColumnWidth) //
            .withMaxMessageColumnWidth(maxMessageColumnWidth) //
            .withMaxReporterColumnWidth(maxReporterColumnWidth) //
            .withMaxRuleColumnWidth(maxRuleColumnWidth) //
            .withMaxSeverityColumnWidth(maxSeverityColumnWidth) //
            .getReport(detailLevel);

    if (tooManyViolations) {
      throw new ScriptException(
          "Too many violations found, max is "
              + maxViolations
              + " but found "
              + violations.size()
              + "\n"
              + report);
    } else {
      if (printViolations) {
        getLogger().info("\nViolations in repo\n\n" + report);
      }
    }
  }

  private void checkDiffViolations(final List<Violation> violations) throws ScriptException {
    final boolean tooManyViolations = violations.size() > diffMaxViolations;
    if (!tooManyViolations && !diffPrintViolations) {
      return;
    }

    final String report =
        violationsReporterApi() //
            .withViolations(violations) //
            .withMaxLineColumnWidth(maxLineColumnWidth) //
            .withMaxMessageColumnWidth(maxMessageColumnWidth) //
            .withMaxReporterColumnWidth(maxReporterColumnWidth) //
            .withMaxRuleColumnWidth(maxRuleColumnWidth) //
            .withMaxSeverityColumnWidth(maxSeverityColumnWidth) //
            .getReport(diffDetailLevel);

    if (tooManyViolations) {
      throw new ScriptException(
          "Too many violations found in diff, max is "
              + diffMaxViolations
              + " but found "
              + violations.size()
              + "\n"
              + report);
    } else {
      if (diffPrintViolations) {
        getLogger().info("\nViolations in diff\n\n" + report);
      }
    }
  }

  private List<Violation> getAllViolationsInDiff(final List<Violation> unfilteredViolations)
      throws Exception {
    if (!isDefined(diffFrom) || !isDefined(diffTo)) {
      getLogger().info("No references specified, will not report violations in diff");
      return new ArrayList<>();
    } else {
      final List<Violation> candidates = getFiltered(unfilteredViolations, diffMinSeverity);
      return new ViolationsGit(candidates) //
          .getViolationsInChangeset(gitRepo, diffFrom, diffTo);
    }
  }

  private List<Violation> getFiltered(final List<Violation> unfiltered, final SEVERITY filter) {
    if (filter != null) {
      return Filtering.withAtLEastSeverity(unfiltered, filter);
    }
    return unfiltered;
  }

  private List<Violation> getAllParsedViolations(final List<String> configuredViolation) {
    final String reporter = configuredViolation.size() >= 4 ? configuredViolation.get(3) : null;

    final List<Violation> parsedViolations =
        violationsApi() //
            .findAll(Parser.valueOf(configuredViolation.get(0))) //
            .inFolder(configuredViolation.get(1)) //
            .withPattern(configuredViolation.get(2)) //
            .withReporter(reporter) //
            .violations();
    return parsedViolations;
  }

  private boolean isDefined(final String str) {
    return str != null && !str.isEmpty();
  }
}
