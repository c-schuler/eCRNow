package com.drajer.cdafromr4;

import com.drajer.cda.utils.CdaGeneratorConstants;
import com.drajer.cda.utils.CdaGeneratorUtils;
import com.drajer.eca.model.MatchedTriggerCodes;
import com.drajer.eca.model.PatientExecutionState;
import com.drajer.ecrapp.util.ApplicationUtils;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.sof.model.R4FhirData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.codesystems.V3ParticipationType;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CdaResultGenerator {

  public static final String OBSERVATION = "Observation";

  private CdaResultGenerator() {}

  private static final Logger logger = LoggerFactory.getLogger(CdaResultGenerator.class);

  public static String generateResultsSection(R4FhirData data, LaunchDetails details) {

    StringBuilder hsb = new StringBuilder(5000);
    StringBuilder textEntries = new StringBuilder(2000);
    StringBuilder resultEntries = new StringBuilder();

    // Get all Lab Results
    List<Observation> allResults = data.getLabResults();
    Map<String, Observation> uniqueObservations = new HashMap<>();

    // Get Diagnostic Reports with lab results and Observations without Diagnostic Reports
    Map<DiagnosticReport, List<Observation>> reports =
        getDiagnosticReportsWithObservations(data, allResults, uniqueObservations);

    if ((uniqueObservations != null && !uniqueObservations.isEmpty())
        || (reports != null && !reports.isEmpty())) {

      // create section header
      createSectionHeader(data, details, hsb);

      // Used for creating the HTML Header in the text section.
      int rowNum = 1;

      // process lab results
      rowNum =
          processLabResults(uniqueObservations, details, rowNum, resultEntries, data, textEntries);

      // process diagnostic reports
      rowNum = processDiagnosticResults(reports, details, rowNum, resultEntries, data, textEntries);

      // complete section
      createSectionEnd(hsb, textEntries, resultEntries);

    } else {
      logger.info(
          " No Lab Results or Diagnostic Reports to generate data , hence generating a null section.");
      hsb.append(generateEmptyLabResults());
    }

    return hsb.toString();
  }

  public static int processLabResults(
      Map<String, Observation> uniqueObservations,
      LaunchDetails details,
      int rowNum,
      StringBuilder resultEntries,
      R4FhirData data,
      StringBuilder textEntries) {

    logger.debug(" Starting to process lab observations ");
    for (Map.Entry<String, Observation> entry : uniqueObservations.entrySet()) {

      if (entry.getValue().hasComponent()) {
        Observation obs = entry.getValue();

        logger.info(
            " Using Observation to create Organizer and Result entry for {}",
            obs.getIdElement().getIdPart());
        // create Table Values
        getTableValuesForObservationWithComponents(obs, rowNum, textEntries);

        // create one organizer and multiple result observations
        processLabResultsWithComponents(
            obs, obs.getCode(), details, rowNum, resultEntries, data, textEntries);

      } else {

        Observation obs = entry.getValue();

        logger.info(
            " Using Observation to create Organizer and Result entry for {}",
            obs.getIdElement().getIdPart());
        // create Table Values
        getTableValues(obs, rowNum, textEntries);

        // create Organizer + result observation entry
        getXmlForLabObservation(obs, obs.getCode(), details, rowNum, resultEntries, data);
      }

      rowNum++;
    }

    return rowNum;
  }

  public static void processLabResultsWithComponents(
      Observation obsWithComponents,
      CodeableConcept cd,
      LaunchDetails details,
      int rowNum,
      StringBuilder resultEntries,
      R4FhirData data,
      StringBuilder textEntries) {

    logger.debug(" Generate XML for Lab Results with Components");
    // Setup the Organizer and Entries
    StringBuilder lrEntry = new StringBuilder(1000);

    // Add static organizer data including start elements for entry and organizer
    lrEntry.append(getOrganizerEntryStaticData());

    // Add dynamic organizer data
    lrEntry.append(
        getOrganizerEntryDynamicData(
            cd,
            details,
            data,
            obsWithComponents.getIssuedElement(),
            obsWithComponents.getPerformer()));

    int i = 1;
    for (ObservationComponentComponent obs : obsWithComponents.getComponent()) {

      // Add result entry component
      lrEntry.append(
          getXmlForObservationComponent(
              details,
              obs.getCode(),
              obs.getValue(),
              (obs.hasId()
                  ? obs.getIdElement().getId()
                  : new String("Component" + Integer.toString(i))),
              obsWithComponents.getEffective(),
              obs.getInterpretation(),
              rowNum,
              obsWithComponents.getPerformer(),
              data));

      i++;
    }

    // End the Organizer and the entry.
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ORGANIZER_EL_NAME));
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ENTRY_EL_NAME));

    resultEntries.append(lrEntry);
  }

  public static void getXmlForLabObservation(
      Observation obs,
      CodeableConcept cd,
      LaunchDetails details,
      int rowNum,
      StringBuilder resultEntries,
      R4FhirData data) {

    logger.debug(" Generating Lab Result XML for Observation ");
    // Setup the Organizer and Entries
    StringBuilder lrEntry = new StringBuilder(1000);

    // Add static organizer data including start elements for entry and organizer
    lrEntry.append(getOrganizerEntryStaticData());

    // Add dynamic organizer data
    lrEntry.append(
        getOrganizerEntryDynamicData(
            obs.getCode(), details, data, obs.getIssuedElement(), obs.getPerformer()));

    // Add result entry component
    lrEntry.append(
        getXmlForObservationComponent(
            details,
            obs.getCode(),
            obs.getValue(),
            obs.getIdElement().getIdPart(),
            obs.getEffective(),
            obs.getInterpretation(),
            rowNum,
            obs.getPerformer(),
            data));

    // End the Organizer and the entry.
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ORGANIZER_EL_NAME));
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ENTRY_EL_NAME));

    resultEntries.append(lrEntry);
  }

  public static String getOrganizerEntryStaticData() {

    StringBuilder lrEntry = new StringBuilder(200);

    // Add the Entries.
    lrEntry.append(CdaGeneratorUtils.getXmlForActEntry(CdaGeneratorConstants.TYPE_CODE_DEF));

    // Add the Organizer Act
    lrEntry.append(
        CdaGeneratorUtils.getXmlForAct(
            CdaGeneratorConstants.ORGANIZER_EL_NAME,
            CdaGeneratorConstants.ORGANIZER_CLASS_CODE_CLUSTER,
            CdaGeneratorConstants.MOOD_CODE_DEF));

    lrEntry.append(
        CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID));
    lrEntry.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID,
            CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID_EXT));

    lrEntry.append(CdaGeneratorUtils.getXmlForIIUsingGuid());

    return lrEntry.toString();
  }

  public static String getOrganizerEntryDynamicData(
      CodeableConcept cd,
      LaunchDetails details,
      R4FhirData data,
      InstantType issued,
      List<Reference> performerReferences) {

    logger.info("Generating Dynamic Data for Organizer ");

    StringBuilder lrEntry = new StringBuilder(200);

    if (cd != null && cd.hasCoding()) {

      logger.debug("Find the Loinc Code as a priority first for Lab Results");
      String codeXml =
          CdaFhirUtilities.getCodingXmlForCodeSystem(
              cd.getCoding(),
              CdaGeneratorConstants.CODE_EL_NAME,
              CdaGeneratorConstants.FHIR_LOINC_URL,
              false,
              "");

      logger.debug("Code Xml = {}", codeXml);

      if (!codeXml.isEmpty()) {
        lrEntry.append(codeXml);
      } else {
        lrEntry.append(
            CdaFhirUtilities.getCodingXml(cd.getCoding(), CdaGeneratorConstants.CODE_EL_NAME, ""));
      }
    } else if (cd != null && cd.hasText()) {

      // Add text value for code in dynamic data
      lrEntry.append(
          CdaGeneratorUtils.getXmlForNullCDWithText(
              CdaGeneratorConstants.CODE_EL_NAME, CdaGeneratorConstants.NF_OTH, cd.getText()));
    } else {
      // Add null flavor for code in dynamic data
      lrEntry.append(CdaFhirUtilities.getCodingXml(null, CdaGeneratorConstants.CODE_EL_NAME, ""));
    }

    lrEntry.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.COMPLETED_STATUS));

    if (issued != null) {
      Pair<Date, TimeZone> issuedDate = CdaFhirUtilities.getActualDate(issued);

      lrEntry.append(
          CdaGeneratorUtils.getXmlForIVLWithTS(
              CdaGeneratorConstants.EFF_TIME_EL_NAME, issuedDate, issuedDate, true));
    }

    if (performerReferences != null && !performerReferences.isEmpty())
      lrEntry.append(getXmlForAuthor(performerReferences, data));

    return lrEntry.toString();
  }

  public static void getTableValuesForObservationWithComponents(
      Observation obs, int rowNum, StringBuilder textEntries) {

    // Add the Panel code name
    String obsDisplayName =
        "Panel Name: " + CdaFhirUtilities.getStringForCodeableConcept(obs.getCode());

    // Add information from Components.
    String val = "";
    if (obs.hasComponent()) {
      for (ObservationComponentComponent o : obs.getComponent()) {
        obsDisplayName +=
            "| Component Name: " + CdaFhirUtilities.getStringForCodeableConcept(o.getCode());

        if (o.hasValue()) {
          val += CdaFhirUtilities.getStringForType(o.getValue());
        }
      }
    }

    // Create the Test Name String
    Map<String, String> bodyvals = new LinkedHashMap<>();
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT, obsDisplayName);

    // Get Value String
    if (val.isEmpty()) val = CdaGeneratorConstants.UNKNOWN_VALUE;
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_2_BODY_CONTENT, val);

    // Get the Date String
    String dt = CdaGeneratorConstants.UNKNOWN_VALUE;
    if (obs.getEffective() != null) {
      dt = CdaFhirUtilities.getStringForType(obs.getEffective());
    }
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_3_BODY_CONTENT, dt);

    textEntries.append(CdaGeneratorUtils.addTableRow(bodyvals, rowNum));
  }

  public static void getTableValues(Observation obs, int rowNum, StringBuilder textEntries) {

    String obsDisplayName = CdaFhirUtilities.getStringForCodeableConcept(obs.getCode());

    // Create the Test Name String
    Map<String, String> bodyvals = new LinkedHashMap<>();
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT, obsDisplayName);

    // Get Value String
    String val = CdaGeneratorConstants.UNKNOWN_VALUE;
    if (obs.hasValue()) {
      val = CdaFhirUtilities.getStringForType(obs.getValue());
    }
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_2_BODY_CONTENT, val);

    // Get the Date String
    String dt = CdaGeneratorConstants.UNKNOWN_VALUE;
    if (obs.getEffective() != null) {
      dt = CdaFhirUtilities.getStringForType(obs.getEffective());
    }
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_3_BODY_CONTENT, dt);

    textEntries.append(CdaGeneratorUtils.addTableRow(bodyvals, rowNum));
  }

  public static int processDiagnosticResults(
      Map<DiagnosticReport, List<Observation>> reports,
      LaunchDetails details,
      int rowNum,
      StringBuilder resultEntries,
      R4FhirData data,
      StringBuilder textEntries) {

    logger.debug(" Starting to process Diagnostic Results ");

    // Iterate through the diagnostic reports
    for (Map.Entry<DiagnosticReport, List<Observation>> entry : reports.entrySet()) {

      DiagnosticReport rep = entry.getKey();
      List<Observation> observations = entry.getValue();

      getTableValuesForDiagnosticReport(rep, observations, rowNum, textEntries);

      logger.info(
          " Using DiagnosticReport to create Organizer and Result entry for {}",
          rep.getIdElement().getIdPart());

      // create one organizer and multiple result observations
      processDiagnosticReportWithObservations(
          rep, observations, details, rowNum, resultEntries, data, textEntries);

      rowNum++;
    }

    return rowNum;
  }

  public static void processDiagnosticReportWithObservations(
      DiagnosticReport report,
      List<Observation> observations,
      LaunchDetails details,
      int rowNum,
      StringBuilder resultEntries,
      R4FhirData data,
      StringBuilder textEntries) {

    logger.info(" Starting to create organizers and entries ");

    StringBuilder lrEntry = new StringBuilder(1000);

    // Add static organizer data including start elements for entry and organizer
    lrEntry.append(getOrganizerEntryStaticData());

    // Add dynamic organizer data
    lrEntry.append(
        getOrganizerEntryDynamicData(
            report.getCode(), details, data, report.getIssuedElement(), report.getPerformer()));

    int i = 1;
    for (Observation obs : observations) {

      // Add result entry component
      lrEntry.append(
          getXmlForObservationComponent(
              details,
              obs.getCode(),
              obs.getValue(),
              (obs.hasId()
                  ? obs.getIdElement().getIdPart()
                  : new String("Component" + Integer.toString(i))),
              obs.getEffective(),
              obs.getInterpretation(),
              rowNum,
              obs.getPerformer(),
              data));

      i++;
    }

    // End the Organizer and the entry.
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ORGANIZER_EL_NAME));
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ENTRY_EL_NAME));

    resultEntries.append(lrEntry);
  }

  public static void getTableValuesForDiagnosticReport(
      DiagnosticReport report, List<Observation> obs, int rowNum, StringBuilder textEntries) {

    // Add the Panel code name
    String obsDisplayName =
        "Panel Name: " + CdaFhirUtilities.getStringForCodeableConcept(report.getCode());

    // Add information from Components.
    String val = "";
    if (obs != null && !obs.isEmpty()) {
      for (Observation o : obs) {
        obsDisplayName +=
            "| Component Name: " + CdaFhirUtilities.getStringForCodeableConcept(o.getCode());

        if (o.hasValue()) {
          val += CdaFhirUtilities.getStringForType(o.getValue());
        }
      }
    }

    // Create the Test Name String
    Map<String, String> bodyvals = new LinkedHashMap<>();
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT, obsDisplayName);

    // Get Value String
    if (val.isEmpty()) val = CdaGeneratorConstants.UNKNOWN_VALUE;
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_2_BODY_CONTENT, val);

    // Get the Date String
    String dt = CdaGeneratorConstants.UNKNOWN_VALUE;
    if (report.hasEffective()) {
      dt = CdaFhirUtilities.getStringForType(report.getEffective());
    }
    bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_3_BODY_CONTENT, dt);

    textEntries.append(CdaGeneratorUtils.addTableRow(bodyvals, rowNum));
  }

  public static Map<DiagnosticReport, List<Observation>> getDiagnosticReportsWithObservations(
      R4FhirData data,
      List<Observation> allObservations,
      Map<String, Observation> uniqueObservations) {

    Map<DiagnosticReport, List<Observation>> reports = new HashMap<>();
    allObservations.forEach(e -> uniqueObservations.put(e.getIdElement().getIdPart(), e));

    if (data.getDiagReports() != null && !data.getDiagReports().isEmpty()) {

      logger.info(
          "Total num of Diagnostic Reports available for Patient {}", data.getDiagReports().size());

      for (DiagnosticReport dr : data.getDiagReports()) {

        if (dr.hasResult()) {

          List<Observation> obs = new ArrayList<>();
          List<Reference> obsRefs = dr.getResult();

          for (Reference r : obsRefs) {

            if (r.hasReference()) {

              // If we find the reference, add it to the overall list
              obs.addAll(
                  allObservations
                      .stream()
                      .filter(
                          s ->
                              s.getIdElement()
                                  .getIdPart()
                                  .contentEquals(r.getReferenceElement().getIdPart()))
                      .collect(Collectors.toList()));
            }
          }

          if (obs != null && !obs.isEmpty()) {

            obs.forEach(e -> uniqueObservations.remove(e.getIdElement().getIdPart()));
            reports.put(dr, obs);
          } else {
            logger.info(
                " Ignoring Diagnostic Report with id {} as observations referenced cannot be found ",
                dr.getIdElement().getIdPart());
          }
        } // if results are present
        else {
          logger.info(
              " Ignoring Diagnostic Report with id {} as it has no results ",
              dr.getIdElement().getIdPart());
        }
      } // for
    } else {
      logger.info("No Valid DiagnosticReport in the bundle to process");
    }

    logger.info(
        " Total # of Diagnostic Reports being used for Results section : {}", reports.size());
    return reports;
  }

  public static void createSectionHeader(
      R4FhirData data, LaunchDetails details, StringBuilder hsb) {

    hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
    hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.SECTION_EL_NAME));

    hsb.append(
        CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID));
    hsb.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID,
            CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID_EXT));

    hsb.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.CODE_EL_NAME,
            CdaGeneratorConstants.LAB_RESULTS_SEC_CODE,
            CdaGeneratorConstants.LOINC_CODESYSTEM_OID,
            CdaGeneratorConstants.LOINC_CODESYSTEM_NAME,
            CdaGeneratorConstants.LAB_RESULTS_SEC_NAME));

    // Add Title
    hsb.append(
        CdaGeneratorUtils.getXmlForText(
            CdaGeneratorConstants.TITLE_EL_NAME, CdaGeneratorConstants.LAB_RESULTS_SEC_TITLE));

    // Add Narrative Text
    hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.TEXT_EL_NAME));

    // Create Table Header.
    List<String> list = new ArrayList<>();
    list.add(CdaGeneratorConstants.LABTEST_TABLE_COL_1_TITLE);
    list.add(CdaGeneratorConstants.LABTEST_TABLE_COL_2_TITLE);
    list.add(CdaGeneratorConstants.LABTEST_TABLE_COL_3_TITLE);
    hsb.append(
        CdaGeneratorUtils.getXmlForTableHeader(
            list, CdaGeneratorConstants.TABLE_BORDER, CdaGeneratorConstants.TABLE_WIDTH));

    // Add Table Body
    hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.TABLE_BODY_EL_NAME));
  }

  public static void createSectionEnd(
      StringBuilder hsb, StringBuilder textEntries, StringBuilder resultEntries) {

    // End the Sb string.
    textEntries.append(
        CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TABLE_BODY_EL_NAME));

    // End Table.
    textEntries.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TABLE_EL_NAME));
    textEntries.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TEXT_EL_NAME));

    hsb.append(textEntries);

    // Add lab results
    hsb.append(resultEntries);

    // Complete the section end tags.
    hsb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.SECTION_EL_NAME));
    hsb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));
  }

  public static String oldgenerateResultsSection(R4FhirData data, LaunchDetails details) {

    StringBuilder hsb = new StringBuilder(5000);
    StringBuilder sb = new StringBuilder(2000);
    StringBuilder resultEntries = new StringBuilder();

    List<Observation> allResults = data.getLabResults();

    // Check if there is a LOINC code for the observation to be translated.
    List<Observation> results = getValidLabResults(data);
    List<DiagnosticReport> reports = getValidDiagnosticReports(data, results);

    if ((results != null && !results.isEmpty()) || (reports != null && !reports.isEmpty())) {

      hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
      hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.SECTION_EL_NAME));

      hsb.append(
          CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID));
      hsb.append(
          CdaGeneratorUtils.getXmlForTemplateId(
              CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID,
              CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID_EXT));

      hsb.append(
          CdaGeneratorUtils.getXmlForCD(
              CdaGeneratorConstants.CODE_EL_NAME,
              CdaGeneratorConstants.LAB_RESULTS_SEC_CODE,
              CdaGeneratorConstants.LOINC_CODESYSTEM_OID,
              CdaGeneratorConstants.LOINC_CODESYSTEM_NAME,
              CdaGeneratorConstants.LAB_RESULTS_SEC_NAME));

      // Add Title
      hsb.append(
          CdaGeneratorUtils.getXmlForText(
              CdaGeneratorConstants.TITLE_EL_NAME, CdaGeneratorConstants.LAB_RESULTS_SEC_TITLE));

      // Add Narrative Text
      hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.TEXT_EL_NAME));

      // Create Table Header.
      List<String> list = new ArrayList<>();
      list.add(CdaGeneratorConstants.LABTEST_TABLE_COL_1_TITLE);
      list.add(CdaGeneratorConstants.LABTEST_TABLE_COL_2_TITLE);
      list.add(CdaGeneratorConstants.LABTEST_TABLE_COL_3_TITLE);
      hsb.append(
          CdaGeneratorUtils.getXmlForTableHeader(
              list, CdaGeneratorConstants.TABLE_BORDER, CdaGeneratorConstants.TABLE_WIDTH));

      // Add Table Body
      hsb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.TABLE_BODY_EL_NAME));

      int rowNum = 1;
      for (Observation obs : results) {

        String obsDisplayName = CdaGeneratorConstants.UNKNOWN_VALUE;
        List<Coding> cds = null;
        if (obs.hasCode()
            && obs.getCode() != null
            && obs.getCode().hasCoding()
            && obs.getCode().getCodingFirstRep() != null) {

          cds = obs.getCode().getCoding();

          if (!StringUtils.isEmpty(obs.getCode().getCodingFirstRep().getDisplay())) {
            obsDisplayName = obs.getCode().getCodingFirstRep().getDisplay();
          } else if (!StringUtils.isEmpty(obs.getCode().getText())) {
            obsDisplayName = obs.getCode().getText();
          } else if (!StringUtils.isEmpty(obs.getCode().getCodingFirstRep().getCode())
              && (!StringUtils.isEmpty(obs.getCode().getCodingFirstRep().getSystem()))) {
            obsDisplayName =
                obs.getCode().getCodingFirstRep().getSystem()
                    + "|"
                    + obs.getCode().getCodingFirstRep().getCode();
          }
        } else if (obs.hasCode()
            && obs.getCode() != null
            && !StringUtils.isEmpty(obs.getCode().getText())) {
          obsDisplayName = obs.getCode().getText();
        }

        Map<String, String> bodyvals = new LinkedHashMap<>();
        bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT, obsDisplayName);

        String val = CdaGeneratorConstants.UNKNOWN_VALUE;
        if (obs.getValue() != null) {

          val = CdaFhirUtilities.getStringForType(obs.getValue());
        }

        bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_2_BODY_CONTENT, val);

        String dt = CdaGeneratorConstants.UNKNOWN_VALUE;
        if (obs.hasEffective()) {

          dt = CdaFhirUtilities.getStringForType(obs.getEffective());
        }
        bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_3_BODY_CONTENT, dt);

        sb.append(CdaGeneratorUtils.addTableRow(bodyvals, rowNum));

        // Setup the Organizer and Entries
        StringBuilder lrEntry = new StringBuilder();

        // Add the Entries.
        lrEntry.append(CdaGeneratorUtils.getXmlForActEntry(CdaGeneratorConstants.TYPE_CODE_DEF));

        // Add the Organizer Act
        lrEntry.append(
            CdaGeneratorUtils.getXmlForAct(
                CdaGeneratorConstants.ORGANIZER_EL_NAME,
                CdaGeneratorConstants.ORGANIZER_CLASS_CODE_CLUSTER,
                CdaGeneratorConstants.MOOD_CODE_DEF));

        lrEntry.append(
            CdaGeneratorUtils.getXmlForTemplateId(
                CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID));
        lrEntry.append(
            CdaGeneratorUtils.getXmlForTemplateId(
                CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID,
                CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID_EXT));

        lrEntry.append(CdaGeneratorUtils.getXmlForIIUsingGuid());

        // Fix the Code to be the same as the result code..
        logger.debug("Find the Loinc Code as a priority first for Lab Results");
        String codeXml =
            CdaFhirUtilities.getCodingXmlForCodeSystem(
                cds,
                CdaGeneratorConstants.CODE_EL_NAME,
                CdaGeneratorConstants.FHIR_LOINC_URL,
                false,
                "");

        logger.debug("Code Xml = {}", codeXml);
        if (!codeXml.isEmpty()) {
          lrEntry.append(codeXml);
        } else {
          lrEntry.append(
              CdaFhirUtilities.getCodingXml(cds, CdaGeneratorConstants.CODE_EL_NAME, ""));
        }

        lrEntry.append(
            CdaGeneratorUtils.getXmlForCD(
                CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.COMPLETED_STATUS));

        if (obs.hasIssued()) {
          Pair<Date, TimeZone> issuedDate = CdaFhirUtilities.getActualDate(obs.getIssuedElement());

          lrEntry.append(
              CdaGeneratorUtils.getXmlForIVLWithTS(
                  CdaGeneratorConstants.EFF_TIME_EL_NAME, issuedDate, issuedDate, true));
        }
        lrEntry.append(getXmlForAuthor(obs.getPerformer(), data));

        lrEntry.append(
            getXmlForObservation(
                details,
                obs,
                CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT,
                rowNum,
                data));

        // End Tags for Entries
        lrEntry.append(
            CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ORGANIZER_EL_NAME));
        lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ENTRY_EL_NAME));

        resultEntries.append(lrEntry);
        rowNum++;
      }

      if (reports != null && !reports.isEmpty()) {
        processDiagnosticResults(reports, allResults, details, rowNum, sb, resultEntries, data);
      }

      // End the Sb string.
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TABLE_BODY_EL_NAME));

      // End Table.
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TABLE_EL_NAME));
      sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.TEXT_EL_NAME));

      hsb.append(sb);

      // Add lab results
      hsb.append(resultEntries);

      // Complete the section end tags.
      hsb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.SECTION_EL_NAME));
      hsb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));

    } else {
      hsb.append(generateEmptyLabResults());
    }

    return hsb.toString();
  }

  public static void processDiagnosticResults(
      List<DiagnosticReport> reports,
      List<Observation> allResults,
      LaunchDetails details,
      int rowNum,
      StringBuilder sb,
      StringBuilder resultEntries,
      R4FhirData data) {

    // Create a map of all Observations to ids for faster lookup
    HashMap<String, Observation> observations = new HashMap<>();

    allResults
        .stream()
        .forEach(
            (obs) -> {
              observations.put(obs.getIdElement().getIdPart(), obs);
            });

    for (DiagnosticReport rep : reports) {

      StringBuilder displayAttr = new StringBuilder(200);
      String repDisplayName = CdaGeneratorConstants.UNKNOWN_VALUE;
      List<Coding> cds = null;
      if (rep.hasCode() && rep.getCode().hasCoding() && rep.getCode().getCodingFirstRep() != null) {

        cds = rep.getCode().getCoding();

        if (!StringUtils.isEmpty(rep.getCode().getCodingFirstRep().getDisplay())) {
          repDisplayName = rep.getCode().getCodingFirstRep().getDisplay();
        } else if (!StringUtils.isEmpty(rep.getCode().getText())) {
          repDisplayName = rep.getCode().getText();
        } else if (!StringUtils.isEmpty(rep.getCode().getCodingFirstRep().getCode())
            && (!StringUtils.isEmpty(rep.getCode().getCodingFirstRep().getSystem()))) {
          repDisplayName =
              rep.getCode().getCodingFirstRep().getSystem()
                  + "|"
                  + rep.getCode().getCodingFirstRep().getCode();
        }
      } else if (rep.getCode() != null && !StringUtils.isEmpty(rep.getCode().getText())) {
        repDisplayName = rep.getCode().getText();
      }

      Map<String, String> bodyvals = new LinkedHashMap<>();
      bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT, repDisplayName);

      String val = CdaGeneratorConstants.UNKNOWN_VALUE;

      if (rep.getResult() != null && rep.getResultFirstRep() != null) {

        val = getResultValueForDiagnosticReport(rep, allResults);
      }

      bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_2_BODY_CONTENT, val);

      String dt = CdaGeneratorConstants.UNKNOWN_VALUE;
      if (rep.getEffective() != null) {

        dt = CdaFhirUtilities.getStringForType(rep.getEffective());
      }
      bodyvals.put(CdaGeneratorConstants.LABTEST_TABLE_COL_3_BODY_CONTENT, dt);

      displayAttr.append(CdaGeneratorUtils.addTableRow(bodyvals, rowNum));

      // Setup the Organizer and Entries
      StringBuilder lrEntry = new StringBuilder();

      // Add the Entries.
      lrEntry.append(CdaGeneratorUtils.getXmlForActEntry(CdaGeneratorConstants.TYPE_CODE_DEF));

      // Add the Organizer Act
      lrEntry.append(
          CdaGeneratorUtils.getXmlForAct(
              CdaGeneratorConstants.ORGANIZER_EL_NAME,
              CdaGeneratorConstants.ORGANIZER_CLASS_CODE_CLUSTER,
              CdaGeneratorConstants.MOOD_CODE_DEF));

      lrEntry.append(
          CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID));
      lrEntry.append(
          CdaGeneratorUtils.getXmlForTemplateId(
              CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID,
              CdaGeneratorConstants.LAB_RESULTS_ORG_TEMPLATE_ID_EXT));

      lrEntry.append(CdaGeneratorUtils.getXmlForIIUsingGuid());

      // Fix the Code to be the same as the result code..
      logger.debug("Find the Loinc Code as a priority first for Lab Results");
      String codeXml =
          CdaFhirUtilities.getCodingXmlForCodeSystem(
              cds,
              CdaGeneratorConstants.CODE_EL_NAME,
              CdaGeneratorConstants.FHIR_LOINC_URL,
              false,
              "");

      logger.debug("Code Xml = {}", codeXml);
      if (!codeXml.isEmpty()) {
        lrEntry.append(codeXml);
      } else {
        lrEntry.append(CdaFhirUtilities.getCodingXml(cds, CdaGeneratorConstants.CODE_EL_NAME, ""));
      }

      lrEntry.append(
          CdaGeneratorUtils.getXmlForCD(
              CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.COMPLETED_STATUS));

      String compXml =
          getXmlForComponents(
              rep,
              observations,
              details,
              CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT,
              rowNum,
              data);

      boolean compFound = false;
      if (compXml != null && !compXml.isEmpty()) {
        lrEntry.append(compXml);
        compFound = true;
      }

      // End Tags for Entries
      lrEntry.append(
          CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ORGANIZER_EL_NAME));
      lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.ENTRY_EL_NAME));

      if (compFound) {
        sb.append(displayAttr.toString());
        resultEntries.append(lrEntry);
      }
      rowNum++;
    }
  }

  public static Observation findObservation(Reference r, List<Observation> allObs) {

    for (Observation obs : allObs) {

      logger.info(
          " Comparing Observation with id {} with reference id {}",
          obs.getIdElement().getIdPart(),
          r.getReferenceElement().getIdPart());
      if (obs.getIdElement().getIdPart().contentEquals(r.getReferenceElement().getIdPart())) {

        return obs;
      }
    }

    return null;
  }

  public static String getXmlForComponents(
      DiagnosticReport rep,
      HashMap<String, Observation> allObs,
      LaunchDetails details,
      String contentId,
      int row,
      R4FhirData data) {

    logger.info(" Adding References to observations ");
    StringBuilder lrEntry = new StringBuilder(2000);
    String contentRef = contentId + Integer.toString(row);

    Boolean foundComponent = false;

    List<Reference> refs = rep.getResult();

    for (Reference r : refs) {

      // Create an Observation for each entry.
      Observation obs = allObs.get(r.getReferenceElement().getIdPart());

      if (obs != null
          && obs.hasComponent()
          && obs.getComponent() != null
          && !obs.getComponent().isEmpty()) {

        CodeableConcept cc = obs.getCode();
        logger.info("CodeableConcept :{}", cc);
        Type val = obs.getValue();
        List<CodeableConcept> interpretation = obs.getInterpretation();
        StringBuilder id = new StringBuilder(200);
        id.append(obs.getIdElement().getIdPart());
        int rowNum = 1;

        for (ObservationComponentComponent oc : obs.getComponent()) {

          logger.debug("Found Observation Components ");
          if (oc.hasCode()
              && oc.getCode().hasCoding()
              && CdaFhirUtilities.isCodingPresentForCodeSystem(
                  oc.getCode().getCoding(), CdaGeneratorConstants.FHIR_LOINC_URL)) {
            cc = oc.getCode();
          } else {
            // use diagnostic report code instead
            cc = rep.getCode();
          }

          if (oc.getValue() != null) {
            val = oc.getValue();
          }

          if (oc.getInterpretation() != null) {
            interpretation = oc.getInterpretation();
          }

          id.append("-");
          id.append(Integer.toBinaryString(rowNum));

          String compString =
              getXmlForObservationComponent(
                  details,
                  cc,
                  val,
                  id.toString(),
                  obs.getEffective(),
                  interpretation,
                  contentRef,
                  null,
                  obs.getPerformer(),
                  data);

          if (!compString.isEmpty() && Boolean.FALSE.equals(foundComponent)) foundComponent = true;

          lrEntry.append(compString);

          rowNum++;
        }
      }

      if (obs != null && Boolean.FALSE.equals(foundComponent)) {

        CodeableConcept cc = null;
        logger.info("No component found , so directly adding the observation code ");
        if (obs.hasCode()
            && obs.getCode().hasCoding()
            && CdaFhirUtilities.isCodingPresentForCodeSystem(
                obs.getCode().getCoding(), CdaGeneratorConstants.FHIR_LOINC_URL)) {
          cc = obs.getCode();
        } else {
          // use diagnostic report code instead
          cc = rep.getCode();
        }

        lrEntry.append(
            getXmlForObservationComponent(
                details,
                cc,
                obs.getValue(),
                obs.getIdElement().getIdPart(),
                obs.getEffective(),
                obs.getInterpretation(),
                contentRef,
                rep.getCode(),
                obs.getPerformer(),
                data));
      }
    }

    logger.debug("Lr Entry = {}", StringEscapeUtils.escapeXml11(lrEntry.toString()));

    return lrEntry.toString();
  }

  public static String getXmlForObservation(
      LaunchDetails details, Observation obs, String contentId, int row, R4FhirData data) {

    StringBuilder lrEntry = new StringBuilder(2000);
    String contentRef = contentId + Integer.toString(row);

    Boolean foundComponent = false;

    if (obs.hasComponent() && obs.getComponent() != null) {

      CodeableConcept cc = obs.getCode();
      Type val = obs.getValue();
      List<CodeableConcept> interpretation = obs.getInterpretation();
      StringBuilder id = new StringBuilder(200);
      id.append(obs.getIdElement().getIdPart());
      int rowNum = 1;

      for (ObservationComponentComponent oc : obs.getComponent()) {

        logger.debug("Found Observation Components ");
        if (oc.getCode() != null) {
          cc = oc.getCode();
        }

        if (oc.getValue() != null) {
          val = oc.getValue();
        }

        if (oc.getInterpretation() != null) {
          interpretation = oc.getInterpretation();
        }

        id.append("-");
        id.append(Integer.toBinaryString(rowNum));

        String compString =
            getXmlForObservationComponent(
                details,
                cc,
                val,
                id.toString(),
                obs.getEffective(),
                interpretation,
                contentRef,
                null,
                obs.getPerformer(),
                data);

        if (!compString.isEmpty()) {
          foundComponent = true;
          lrEntry.append(compString);
        }

        rowNum++;
      }
    }

    if (Boolean.FALSE.equals(foundComponent)) {

      logger.debug("No component found , so directly adding the observation code ");
      lrEntry.append(
          getXmlForObservationComponent(
              details,
              obs.getCode(),
              obs.getValue(),
              obs.getIdElement().getIdPart(),
              obs.getEffective(),
              obs.getInterpretation(),
              contentRef,
              null,
              obs.getPerformer(),
              data));
    }

    logger.debug("Lr Entry = {}", StringEscapeUtils.escapeXml11(lrEntry.toString()));

    return lrEntry.toString();
  }

  public static String getXmlForObservationComponent(
      LaunchDetails details,
      CodeableConcept cd,
      Type val,
      String id,
      Type effective,
      List<CodeableConcept> interpretation,
      int rowNum,
      List<Reference> performerRefs,
      R4FhirData data) {

    StringBuilder lrEntry = new StringBuilder(2000);

    // Add the actual Result Observation
    lrEntry.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
    lrEntry.append(
        CdaGeneratorUtils.getXmlForAct(
            CdaGeneratorConstants.OBS_ACT_EL_NAME,
            CdaGeneratorConstants.OBS_CLASS_CODE,
            CdaGeneratorConstants.MOOD_CODE_DEF));

    lrEntry.append(
        CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID));
    lrEntry.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID,
            CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID_EXT));

    List<String> paths = new ArrayList<>();
    paths.add("Observation.code");
    paths.add("DiagnosticReport.code");
    paths.add("Observation.value");

    String contentRef =
        CdaGeneratorConstants.LABTEST_TABLE_COL_1_BODY_CONTENT + Integer.toString(rowNum);
    Pair<Boolean, String> obsCodeXml = getObservationCodeXml(details, cd, false, contentRef, paths);

    Pair<Boolean, String> obsValueXml = null;
    contentRef = CdaGeneratorConstants.LABTEST_TABLE_COL_2_BODY_CONTENT + Integer.toString(rowNum);
    if (val instanceof CodeableConcept) {
      paths.clear();
      paths.add("Observation.value");
      CodeableConcept value = (CodeableConcept) val;
      obsValueXml = getObservationCodeXml(details, value, true, contentRef, paths);
    }

    if ((obsCodeXml != null && obsCodeXml.getValue0())
        || (obsValueXml != null && obsValueXml.getValue0())) {

      lrEntry.append(
          CdaGeneratorUtils.getXmlForTemplateId(
              CdaGeneratorConstants.LAB_TEST_RESULT_OBSERVATION_TRIGGER_TEMPLATE,
              CdaGeneratorConstants.LAB_TEST_RESULT_OBSERVATION_TRIGGER_TEMPLATE_EXT));
    }

    lrEntry.append(CdaGeneratorUtils.getXmlForII(details.getAssigningAuthorityId(), id));

    if (obsCodeXml != null) {
      lrEntry.append(obsCodeXml.getValue1());
    } else {
      logger.warn("Unable to add code as the xml is null");
    }

    lrEntry.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.COMPLETED_STATUS));

    lrEntry.append(
        CdaFhirUtilities.getXmlForType(effective, CdaGeneratorConstants.EFF_TIME_EL_NAME, false));

    if (obsValueXml == null) {
      lrEntry.append(CdaFhirUtilities.getXmlForType(val, CdaGeneratorConstants.VAL_EL_NAME, true));
    } else {
      lrEntry.append(obsValueXml.getValue1());
    }

    // Add interpretation code.
    if (interpretation != null) {

      logger.debug("Adding Interpretation Code");

      String interpretXml =
          CdaFhirUtilities.getCodeableConceptXmlForMappedConceptDomain(
              CdaGeneratorConstants.INTERPRETATION_CODE_EL_NAME,
              interpretation,
              CdaGeneratorConstants.INTERPRETATION_CODE_EL_NAME,
              false,
              false);

      if (interpretXml != null && !interpretXml.isEmpty()) lrEntry.append(interpretXml);
    }

    // Add performer
    lrEntry.append(getXmlForAuthor(performerRefs, data));

    // End Tag for Entry Relationship
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.OBS_ACT_EL_NAME));
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));

    logger.debug("Lr Entry = {}", StringEscapeUtils.escapeXml11(lrEntry.toString()));

    return lrEntry.toString();
  }

  public static String getXmlForObservationComponent(
      LaunchDetails details,
      CodeableConcept cd,
      Type val,
      String id,
      Type effective,
      List<CodeableConcept> interpretation,
      String contentRef,
      CodeableConcept altCode,
      List<Reference> performerRefs,
      R4FhirData data) {

    StringBuilder lrEntry = new StringBuilder(2000);

    // Add the actual Result Observation
    lrEntry.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
    lrEntry.append(
        CdaGeneratorUtils.getXmlForAct(
            CdaGeneratorConstants.OBS_ACT_EL_NAME,
            CdaGeneratorConstants.OBS_CLASS_CODE,
            CdaGeneratorConstants.MOOD_CODE_DEF));

    lrEntry.append(
        CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID));
    lrEntry.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID,
            CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID_EXT));

    List<String> paths = new ArrayList<>();
    paths.add("Observation.code");
    paths.add("DiagnosticReport.code");
    paths.add("Observation.value");

    Pair<Boolean, String> obsCodeXml = getObservationCodeXml(details, cd, false, contentRef, paths);

    Pair<Boolean, String> altObsCodeXml = null;
    if (altCode != null) {
      altObsCodeXml = getObservationCodeXml(details, altCode, false, contentRef, paths);
    }

    Pair<Boolean, String> obsValueXml = null;

    if (val instanceof CodeableConcept) {

      paths.clear();
      paths.add("Observation.value");
      CodeableConcept value = (CodeableConcept) val;
      obsValueXml = getObservationCodeXml(details, value, true, contentRef, paths);
    }

    if ((obsCodeXml != null && obsCodeXml.getValue0())
        || (obsValueXml != null && obsValueXml.getValue0())) {

      lrEntry.append(
          CdaGeneratorUtils.getXmlForTemplateId(
              CdaGeneratorConstants.LAB_TEST_RESULT_OBSERVATION_TRIGGER_TEMPLATE,
              CdaGeneratorConstants.LAB_TEST_RESULT_OBSERVATION_TRIGGER_TEMPLATE_EXT));

    } else if (altObsCodeXml != null && altObsCodeXml.getValue0()) {

      // this will catch the case the DiagnosticReport.code is matched and the
      // Observation.code does
      // not exist
      // or is not the same.
      lrEntry.append(
          CdaGeneratorUtils.getXmlForTemplateId(
              CdaGeneratorConstants.LAB_TEST_RESULT_OBSERVATION_TRIGGER_TEMPLATE,
              CdaGeneratorConstants.LAB_TEST_RESULT_OBSERVATION_TRIGGER_TEMPLATE_EXT));
    }

    lrEntry.append(CdaGeneratorUtils.getXmlForII(details.getAssigningAuthorityId(), id));

    if (obsCodeXml != null && obsCodeXml.getValue0()) {
      lrEntry.append(obsCodeXml.getValue1());
    } else if (altObsCodeXml != null && altObsCodeXml.getValue0()) {
      lrEntry.append(altObsCodeXml.getValue1());
    } else if (obsCodeXml != null) {
      lrEntry.append(obsCodeXml.getValue1());
    } else if (altObsCodeXml != null) {
      lrEntry.append(altObsCodeXml.getValue1());
    }

    lrEntry.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.COMPLETED_STATUS));

    lrEntry.append(
        CdaFhirUtilities.getXmlForType(effective, CdaGeneratorConstants.EFF_TIME_EL_NAME, false));

    if (obsValueXml == null) {
      lrEntry.append(CdaFhirUtilities.getXmlForType(val, CdaGeneratorConstants.VAL_EL_NAME, true));
    } else {
      lrEntry.append(obsValueXml.getValue1());
    }

    // Add interpretation code.
    if (interpretation != null) {

      logger.debug("Adding Interpretation Code");

      String interpretXml =
          CdaFhirUtilities.getCodeableConceptXmlForMappedConceptDomain(
              CdaGeneratorConstants.INTERPRETATION_CODE_EL_NAME,
              interpretation,
              CdaGeneratorConstants.INTERPRETATION_CODE_EL_NAME,
              false,
              false);

      if (interpretXml != null && !interpretXml.isEmpty()) lrEntry.append(interpretXml);
    }

    // Add performer

    lrEntry.append(getXmlForAuthor(performerRefs, data));

    // End Tag for Entry Relationship
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.OBS_ACT_EL_NAME));
    lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));

    return lrEntry.toString();
  }

  public static Pair<Boolean, String> getObservationCodeXml(
      LaunchDetails details,
      CodeableConcept code,
      Boolean valElement,
      String contentRef,
      List<String> paths) {

    String elementType =
        valElement ? CdaGeneratorConstants.VAL_EL_NAME : CdaGeneratorConstants.CODE_EL_NAME;
    PatientExecutionState state = ApplicationUtils.getDetailStatus(details);
    List<MatchedTriggerCodes> mtcs = state.getMatchTriggerStatus().getMatchedCodes();

    for (MatchedTriggerCodes mtc : mtcs) {
      Pair<String, String> matchedCode = findMatchingCode(mtc, code, paths);
      if (matchedCode != null) {
        String systemUrl =
            valElement
                ? CdaGeneratorConstants.FHIR_SNOMED_URL
                : CdaGeneratorConstants.FHIR_LOINC_URL;

        logger.info("Found a matched {} for the observation or diagnostic report", elementType);

        Pair<String, String> systemName =
            CdaGeneratorConstants.getCodeSystemFromUrl(matchedCode.getValue1());
        String xml =
            CdaFhirUtilities.getXmlForCodeableConceptWithCDAndValueSetAndVersion(
                elementType,
                matchedCode.getValue0(),
                systemName.getValue0(),
                systemName.getValue1(),
                details.getRctcOid(),
                details.getRctcVersion(),
                code,
                systemUrl,
                contentRef,
                valElement);
        return new Pair<>(true, xml);
      }
    }

    logger.info("Did not find a matched Code or value for the observation or diagnostic report");

    String xml =
        valElement
            ? CdaFhirUtilities.getCodeableConceptXmlForValue(code, elementType, contentRef)
            : CdaFhirUtilities.getCodeableConceptXml(code, elementType, contentRef);
    return new Pair<>(false, xml);
  }

  public static String addTriggerCodes(LaunchDetails details, Observation obs, List<Coding> cds) {

    StringBuilder lrEntry = new StringBuilder();

    logger.debug("Adding Trigger Code Reason for Result Observation");

    PatientExecutionState state = null;

    state = ApplicationUtils.getDetailStatus(details);

    List<MatchedTriggerCodes> mtcs = state.getMatchTriggerStatus().getMatchedCodes();

    for (MatchedTriggerCodes mtc : mtcs) {

      // Add each code as an entry relationship observation

      if (Boolean.TRUE.equals(mtc.hasMatchedTriggerCodes(OBSERVATION))) {

        // Add the actual Result Observation
        lrEntry.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
        lrEntry.append(
            CdaGeneratorUtils.getXmlForAct(
                CdaGeneratorConstants.OBS_ACT_EL_NAME,
                CdaGeneratorConstants.OBS_CLASS_CODE,
                CdaGeneratorConstants.MOOD_CODE_DEF));

        lrEntry.append(
            CdaGeneratorUtils.getXmlForTemplateId(
                CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID));
        lrEntry.append(
            CdaGeneratorUtils.getXmlForTemplateId(
                CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID,
                CdaGeneratorConstants.LAB_RESULTS_ENTRY_TEMPLATE_ID_EXT));
        lrEntry.append(
            CdaGeneratorUtils.getXmlForTemplateId(
                CdaGeneratorConstants.TRIGGER_CODE_LAB_RESULT_TEMPLATE_ID,
                CdaGeneratorConstants.TRIGGER_CODE_LAB_RESULT_TEMPLATE_ID_EXT));

        lrEntry.append(
            CdaGeneratorUtils.getXmlForII(
                details.getAssigningAuthorityId(), obs.getIdElement().getIdPart()));

        String codeXml =
            CdaFhirUtilities.getCodingXmlForCodeSystem(
                cds,
                CdaGeneratorConstants.CODE_EL_NAME,
                CdaGeneratorConstants.FHIR_LOINC_URL,
                true,
                "");

        if (!codeXml.isEmpty()) {
          lrEntry.append(codeXml);
        } else {
          lrEntry.append(
              CdaFhirUtilities.getCodingXml(cds, CdaGeneratorConstants.CODE_EL_NAME, ""));
        }

        lrEntry.append(
            CdaGeneratorUtils.getXmlForCD(
                CdaGeneratorConstants.STATUS_CODE_EL_NAME, CdaGeneratorConstants.COMPLETED_STATUS));

        lrEntry.append(
            CdaFhirUtilities.getXmlForType(
                obs.getEffective(), CdaGeneratorConstants.EFF_TIME_EL_NAME, false));

        Set<String> matchedCodes = mtc.getMatchedCodes();

        if (matchedCodes != null && !matchedCodes.isEmpty()) {

          // Split the system and code.
          matchedCodes
              .stream()
              .filter(Objects::nonNull)
              .findFirst()
              .ifPresent(
                  matchCode -> {
                    String[] parts = matchCode.split("\\|");

                    Pair<String, String> csd = CdaGeneratorConstants.getCodeSystemFromUrl(parts[0]);

                    // For Connectathon, until we get the right test data finish testing.
                    lrEntry.append(
                        CdaGeneratorUtils.getXmlForValueCDWithValueSetAndVersion(
                            parts[1],
                            csd.getValue0(),
                            csd.getValue1(),
                            details.getRctcOid(),
                            details.getRctcVersion(),
                            ""));

                    // Adding one is sufficient and only one is possible according to Schema.
                  });
        }

        // End Tag for Entry Relationship
        lrEntry.append(
            CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.OBS_ACT_EL_NAME));
        lrEntry.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));

      } else {

        logger.debug("Not adding matched Trigger codes as they are not present for Lab Result");
      }
    }

    return lrEntry.toString();
  }

  public static String generateEmptyLabResults() {
    StringBuilder sb = new StringBuilder();

    // Generate the component and section end tags
    sb.append(CdaGeneratorUtils.getXmlForStartElement(CdaGeneratorConstants.COMP_EL_NAME));
    sb.append(
        CdaGeneratorUtils.getXmlForNFSection(
            CdaGeneratorConstants.SECTION_EL_NAME, CdaGeneratorConstants.NF_NI));

    sb.append(
        CdaGeneratorUtils.getXmlForTemplateId(CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID));
    sb.append(
        CdaGeneratorUtils.getXmlForTemplateId(
            CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID,
            CdaGeneratorConstants.LAB_RESULTS_SEC_TEMPLATE_ID_EXT));

    sb.append(
        CdaGeneratorUtils.getXmlForCD(
            CdaGeneratorConstants.CODE_EL_NAME,
            CdaGeneratorConstants.LAB_RESULTS_SEC_CODE,
            CdaGeneratorConstants.LOINC_CODESYSTEM_OID,
            CdaGeneratorConstants.LOINC_CODESYSTEM_NAME,
            CdaGeneratorConstants.LAB_RESULTS_SEC_NAME));

    // Add Title
    sb.append(
        CdaGeneratorUtils.getXmlForText(
            CdaGeneratorConstants.TITLE_EL_NAME, CdaGeneratorConstants.LAB_RESULTS_SEC_TITLE));

    // Add Narrative Text
    sb.append(
        CdaGeneratorUtils.getXmlForText(
            CdaGeneratorConstants.TEXT_EL_NAME, "No Lab Results Information"));

    // Complete the section end tags.
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.SECTION_EL_NAME));
    sb.append(CdaGeneratorUtils.getXmlForEndElement(CdaGeneratorConstants.COMP_EL_NAME));

    return sb.toString();
  }

  public static List<Observation> getValidLabResults(R4FhirData data) {

    List<Observation> sr = new ArrayList<>();

    if (data.getLabResults() != null && !data.getLabResults().isEmpty()) {

      logger.info("Total num of Lab Results available for Patient {}", data.getLabResults().size());

      for (Observation s : data.getLabResults()) {

        if (s.hasCode()
            && s.getCode() != null
            && s.getCode().hasCoding()
            && s.getCode().getCoding() != null
            && !s.getCode().getCoding().isEmpty()
            && Boolean.TRUE.equals(
                CdaFhirUtilities.isCodingPresentForCodeSystem(
                    s.getCode().getCoding(), CdaGeneratorConstants.FHIR_LOINC_URL))) {

          logger.debug("Found a Lab Results with a LOINC code");
          sr.add(s);
        } else {
          logger.info(
              " Ignoring observation with id {} because it is not coded with LOINC code",
              s.getIdElement().getIdPart());
        }
      }
    } else {
      logger.debug("No Valid Lab Results in the bundle to process");
    }

    return sr;
  }

  public static String getXmlForAuthor(List<Reference> performerRefs, R4FhirData data) {
    StringBuilder sb = new StringBuilder();
    if (data == null || performerRefs == null || performerRefs.isEmpty()) {
      return sb.toString();
    }

    for (Reference reference : performerRefs) {
      if (reference.hasReferenceElement()
          && reference.getReferenceElement().hasResourceType()
          && ResourceType.fromCode(reference.getReferenceElement().getResourceType())
              == ResourceType.Practitioner) {

        Practitioner pract = data.getPractitionerById(reference.getReferenceElement().getIdPart());
        if (pract != null) {
          HashMap<V3ParticipationType, List<Practitioner>> practMap = new HashMap<>();
          practMap.put(V3ParticipationType.AUT, Collections.singletonList(pract));
          sb.append(CdaHeaderGenerator.getAuthorXml(data, data.getEncounter(), practMap));
        }
      }
    }
    return sb.toString();
  }

  public static List<DiagnosticReport> getValidDiagnosticReports(
      R4FhirData data, List<Observation> results) {

    List<DiagnosticReport> drs = new ArrayList<>();
    if (data.getDiagReports() != null && !data.getDiagReports().isEmpty()) {

      logger.info(
          "Total num of Diagnostic Reports available for Patient {}", data.getDiagReports().size());

      for (DiagnosticReport dr : data.getDiagReports()) {

        if (dr.hasCode()
            && dr.getCode().hasCoding()
            && !dr.getCode().getCoding().isEmpty()
            && dr.hasResult()
            && !dr.getResult().isEmpty()
            && Boolean.TRUE.equals(
                CdaFhirUtilities.isCodingPresentForCodeSystem(
                    dr.getCode().getCoding(), CdaGeneratorConstants.FHIR_LOINC_URL))) {

          logger.debug("Found a DiagnosticReport with a LOINC code");

          if (dr.hasResult() && (results != null && !results.isEmpty())) {
            filterLabResultsWithReferences(dr.getResult(), results);
          }

          drs.add(dr);
        } else {
          logger.info(
              " Ignoring Diagnostic Report with id {} since the data cannot be used to create an Organizer or POT Observation ",
              dr.getIdElement().getIdPart());
        }
      }
    } else {
      logger.info("No Valid DiagnosticReport in the bundle to process");
    }

    return drs;
  }

  public static List<Observation> filterLabResultsWithReferences(
      List<Reference> resultRefs, List<Observation> results) {

    Set<String> observationIds = new HashSet<>();
    for (Reference reference : resultRefs) {
      if (reference.hasReferenceElement()) {
        IIdType referenceElement = reference.getReferenceElement();
        if (referenceElement.hasResourceType()
            && ResourceType.fromCode(referenceElement.getResourceType())
                == ResourceType.Observation) {
          observationIds.add(referenceElement.getIdPart());
        }
      }
    }

    if (!observationIds.isEmpty()) {
      results.removeIf(
          observation -> {
            String obsId =
                Optional.ofNullable(observation.getIdElement()).map(IdType::getIdPart).orElse(null);
            return obsId != null && observationIds.contains(obsId);
          });
    }
    return results;
  }

  public static String getResultValueForDiagnosticReport(
      DiagnosticReport dr, List<Observation> obsList) {

    String retVal = "";

    if (dr.hasResult()) {

      Boolean first = true;
      String res = "Result#";
      int counter = 1;
      String delim = ":";
      for (Reference r : dr.getResult()) {
        Observation obs = findObservation(r, obsList);

        if (obs != null) {
          if (first) {
            retVal += res + Integer.toString(counter) + delim + getResultValueForObservation(obs);
            counter++;
          } else {
            retVal +=
                " | " + res + Integer.toString(counter) + delim + getResultValueForObservation(obs);
            counter++;
          }

          first = false;
        }
      }
    }

    return retVal;
  }

  public static String getResultValueForObservation(Observation obs) {

    String retVal = "";

    if (obs.hasValue()) {
      retVal = CdaFhirUtilities.getStringForType(obs.getValue());
    } else if (obs.hasComponent()) {

      for (ObservationComponentComponent c : obs.getComponent()) {

        Boolean first = true;
        String comp = "Component#";
        int counter = 1;
        String delim = ":";

        if (first && obs.hasValue()) {
          retVal +=
              comp
                  + Integer.toString(counter)
                  + delim
                  + CdaFhirUtilities.getStringForType(obs.getValue());
          counter++;
        } else if (obs.hasValue()) {
          retVal +=
              " | "
                  + comp
                  + Integer.toString(counter)
                  + delim
                  + CdaFhirUtilities.getStringForType(obs.getValue());
          counter++;
        }

        first = false;
      }
    }

    if (StringUtils.isEmpty(retVal)) return CdaGeneratorConstants.UNKNOWN_VALUE;
    else return retVal;
  }

  private static Pair<String, String> findMatchingCode(
      MatchedTriggerCodes mtc, CodeableConcept code, List<String> paths) {

    for (String s : paths) {
      Pair<String, String> matchedCode = mtc.getMatchingCode(code, s);
      if (matchedCode != null) {
        return matchedCode;
      }
    }
    return null; // Indicate no matching code found
  }
}
