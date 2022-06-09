package projects.bbmri

import de.kairos.fhir.centraxx.metamodel.IdContainer
import de.kairos.fhir.centraxx.metamodel.IdContainerType

import static de.kairos.fhir.centraxx.metamodel.RootEntities.sample

/**
 * Represented by a CXX AbstractSample
 * Specified by https://simplifier.net/bbmri.de/specimen
 *
 * hints:
 * The CCP-IT JF on 2020-12-18 has decides/informed that only master samples should be exported for the BBMRI-Sample locator.
 * Because the DKTK uses also all Aliquots (yet), a separate Groovy mapping for the same profile is necessary for DKTK.
 * //TODO: think about further Sample constrains for export, e.g. restAmount > 0, has no other sampleAbstractions, has child aliquots, etc.
 *
 * @author Mike Wähnert
 * @since CXX.v.3.17.0.2
 */
specimen {

  if (!"MASTER".equals(context.source["sampleCategory"])) {
    return  // all not master are filtered.
  }

  id = "Specimen/" + context.source["id"]

  meta {
    profile "https://fhir.bbmri.de/StructureDefinition/Specimen"
  }

  final def idc = context.source[sample().idContainer()].find {
    "EXLIQUID" == it[IdContainer.ID_CONTAINER_TYPE]?.getAt(IdContainerType.CODE)
  }

  if (idc) {
    identifier {
      value = idc[IdContainer.PSN]
      type {
        coding {
          system = "urn:centraxx"
          code = idc[IdContainer.ID_CONTAINER_TYPE]?.getAt(IdContainerType.CODE)
        }
      }
      system = "https://dktk.dkfz.de/fhir/NamingSystem/exliquid-specimen"
    }
  }

  status = context.source["restAmount.amount"] > 0 ? "available" : "unavailable"

  type {
    coding {
      system = "urn:centraxx"
      code = context.source["sampleType.code"]
    }
    if (context.source["sampleType.sprecCode"]) {
      coding += context.translateBuiltinConcept("sprec3_bbmri_sampletype", context.source["sampleType.sprecCode"])
      coding {
        system = "https://doi.org/10.1089/bio.2017.0109"
        code = context.source["sampleType.sprecCode"]
      }
    } else {
      coding += context.translateBuiltinConcept("centraxx_bbmri_samplekind", context.source["sampleType.kind"] ?: "")
    }
  }

  subject {
    reference = "Patient/" + context.source["patientcontainer.id"]
  }

  receivedTime {
    date = normalizeDate(context.source["samplingDate.date"] as String)
  }

  final def ucum = context.conceptMaps.builtin("centraxx_ucum")
  collection {
    collectedDateTime {
      date = context.source["samplingDate.date"]
      quantity {
        value = context.source["initialAmount.amount"] as Number
        unit = ucum.translate(context.source["initialAmount.unit"] as String)?.code
        system = "http://unitsofmeasure.org"
      }
    }
  }

  container {
    if (context.source["receptable"]) {
      identifier {
        value = context.source["receptable.code"]
        system = "urn:centraxx"
      }

      capacity {
        value = context.source["receptable.size"]
        unit = ucum.translate(context.source["restAmount.unit"] as String)?.code
        system = "http://unitsofmeasure.org"
      }
    }

    specimenQuantity {
      value = context.source["restAmount.amount"] as Number
      unit = ucum.translate(context.source["restAmount.unit"] as String)?.code
      system = "http://unitsofmeasure.org"
    }
  }


  if (context.source["organisationUnit"]) {
    extension {
      url = "https://fhir.bbmri.de/StructureDefinition/Custodian"
      valueReference {
        reference = "Organization/" + context.source["organisationUnit.id"]
      }
    }
  }

  if (context.source[sample().diagnosis()]) {
    extension {
      url = "https://fhir.bbmri.de/StructureDefinition/SampleDiagnosis"
      valueCodeableConcept {
        coding {
          system = "http://hl7.org/fhir/sid/icd-10"
          code = context.source[sample().diagnosis().diagnosisCode()]
        }
      }
    }
  }

  final def temperature = toTemperature(context)
  if (temperature) {
    extension {
      url = "https://fhir.bbmri.de/StructureDefinition/StorageTemperature"
      valueCodeableConcept {
        coding {
          system = "https://fhir.bbmri.de/CodeSystem/StorageTemperature"
          code = temperature
        }
      }
    }
  }
}

static def toTemperature(final ctx) {
  final def temp = ctx.source["sampleLocation.temperature"]

  if (null != temp) {
    switch (temp) {
      case { it >= 2.0 && it <= 10 }: return "temperature2to10"
      case { it <= -18.0 && it >= -35.0 }: return "temperature-18to-35"
      case { it <= -60.0 && it >= -85.0 }: return "temperature-60to-85"
    }
  }

  final def sprec = ctx.source["receptable.sprecCode"]
  if (null != sprec) {
    switch (sprec) {
      case ['C', 'F', 'O', 'Q']: return "temperatureLN"
      case ['A', 'D', 'J', 'L', 'N', 'O', 'S']: return "temperature-60to-85"
      case ['B', 'H', 'K', 'M', 'T']: return "temperature-18to-35"
      default: return "temperatureOther"
    }
  }

  return null
}

static String normalizeDate(final String dateTimeString) {
  return dateTimeString != null ? dateTimeString.substring(0, 10) : null // removes the time
}
