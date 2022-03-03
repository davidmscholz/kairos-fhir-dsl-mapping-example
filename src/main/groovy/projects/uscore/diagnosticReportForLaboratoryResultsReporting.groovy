package projects.uscore

import de.kairos.centraxx.fhir.r4.utils.FhirUrls
import de.kairos.fhir.centraxx.metamodel.LaborFindingLaborValue
import de.kairos.fhir.centraxx.metamodel.MultilingualEntry
import de.kairos.fhir.centraxx.metamodel.enums.LaborMethodCategory
import org.hl7.fhir.r4.model.DiagnosticReport

import static de.kairos.fhir.centraxx.metamodel.AbstractEntity.ID
import static de.kairos.fhir.centraxx.metamodel.RootEntities.laborMapping

/**
 * Represents a CXX LaborMapping.
 * Specified by https://www.hl7.org/fhir/us/core/StructureDefinition-us-core-diagnosticreport-lab.html
 *
 * @author Jonas Küttner
 * @since v.1.13.0, CXX.v.2022.1.0
 *
 */

final def lang = "de"

diagnosticReport {
  if (context.source[laborMapping().laborFinding().laborMethod().category()] != LaborMethodCategory.LABOR.toString()){
    return
  }
  // filter for lblvs that are not files
  final def lflvs = context.source[laborMapping().laborFinding().laborFindingLaborValues()].findAll {
    final def lflv -> !lflv[LaborFindingLaborValue.FILE_VALUE]
  }

  if (lflvs.isEmpty()) {
    return
  }

  id = "DiagnosticReport/" + context.source[laborMapping().laborFinding().id()]
  language = lang
  status = DiagnosticReport.DiagnosticReportStatus.UNKNOWN

  category {
    coding {
      system = "http://terminology.hl7.org/CodeSystem/v2-0074"
      code = "LAB"
    }
  }

  category {
    coding {
      system = FhirUrls.System.LaborMethod.Category.BASE_URL
      code = context.source[laborMapping().laborFinding().laborMethod().category()]as String
    }
  }

  code {
    coding {
      system = FhirUrls.System.LaborMethod.BASE_URL
      code = context.source[laborMapping().laborFinding().laborMethod().code()]as String
      display = context.source[laborMapping().laborFinding().laborMethod().nameMultilingualEntries()]?.find {
        final def ml -> ml[MultilingualEntry.LANG] == lang
      }?.getAt(MultilingualEntry.VALUE)
    }
  }

  final def relatedPatient = context.source[laborMapping().relatedPatient()]

  if (relatedPatient) {
    subject {
      reference = "Patient/" + relatedPatient[ID]
    }
  }

  effectiveDateTime {
    date = context.source[laborMapping().laborFinding().findingDate().date()]
  }

  issued = normalizeDate(context.source[laborMapping().laborFinding().creationDate()] as String)

  lflvs.each {
    final def lflv ->
      result {
        reference = "Observation/" + lflv[ID]
      }
  }


}

static String normalizeDate(final String dateTimeString) {
  return dateTimeString != null ? dateTimeString.substring(0, 19) : null
}