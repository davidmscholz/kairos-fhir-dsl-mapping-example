package projects.dktk.v2


import static de.kairos.fhir.centraxx.metamodel.AbstractCode.CODE
import static de.kairos.fhir.centraxx.metamodel.AbstractIdContainer.ID_CONTAINER_TYPE
import static de.kairos.fhir.centraxx.metamodel.AbstractIdContainer.PSN
import static de.kairos.fhir.centraxx.metamodel.RootEntities.abstractSample
import static de.kairos.fhir.centraxx.metamodel.RootEntities.sample

/**
 * Represented by a CXX AbstractSample
 *
 * Specified by https://simplifier.net/bbmri.de/specimen
 *
 * hints:
 * The DKTK oncology profiles does not contain a separate specimen, instead of the BBMRI specimen should be used. Unfortunately,
 * the BBMRI specifies another Organization (https://simplifier.net/bbmri.de/collection) than the DKTK oncology, which is much different.
 * To avoid conflicts between both organization profiles, the specimen collection extension has been removed.
 *
 * @author Mike Wähnert
 * @since CXX.v.3.17.1.6, v.3.17.2
 */
specimen {

  id = "Specimen/" + context.source[abstractSample().id()]

  meta {
    profile "https://fhir.bbmri.de/StructureDefinition/Specimen"
  }

  context.source[abstractSample().idContainer()]?.each { final idc ->
    identifier {
      value = idc[PSN]
      type {
        coding {
          system = "urn:centraxx"
          code = idc[ID_CONTAINER_TYPE]?.getAt(CODE)
        }
      }
      system = "urn:centraxx"
    }
  }

  status = context.source[abstractSample().restAmount().amount()] > 0 ? "available" : "unavailable"

  type {
    // First coding has the CXX sample type code. If you miss a mapping, this code might help you to identify the source value.
    coding {
      system = "urn:centraxx"
      code = context.source[abstractSample().sampleType().code()]
    }

    // If the CXX sample type contains a SPREC code, mapping against lvl 2 otherwise against lvl1 codes of BBMRI SampleMaterialType is tried.
    if (context.source[abstractSample().sampleType().sprecCode()]) {
      final String bbmriCode = sprecToBbmriSampleType(context.source[abstractSample().sampleType().sprecCode()] as String)
      if (bbmriCode != null) {
        coding {
          system = "https://fhir.bbmri.de/CodeSystem/SampleMaterialType"
          code = bbmriCode
        }
      }
      coding { // SPREC code is exported as second coding to identify mapping leaks.
        system = "https://doi.org/10.1089/bio.2017.0109"
        code = context.source[abstractSample().sampleType().sprecCode()]
      }
    }
    // common cases without mapping
    else if (["DNA", "cf-dna", " g-dna",
              "blood-plasma", "plasma-edta", "plasma-citrat", "plasma-heparin", "plasma-cell-free", "plasma-other"
    ].contains(context.source[abstractSample().sampleType().code()] as String)) {
      coding {
        system = "https://fhir.bbmri.de/CodeSystem/SampleMaterialType"
        code = context.source[abstractSample().sampleType().code()]
      }
    } else {
      final String bbmriCode = sampleKindToBbmriSampleType(context.source[abstractSample().sampleType().kind()] as String)
      if (bbmriCode != null) {
        coding {
          system = "https://fhir.bbmri.de/CodeSystem/SampleMaterialType"
          code = bbmriCode
        }
      }
    }
  }

  subject {
    reference = "Patient/" + context.source[abstractSample().patientContainer().id()]
  }

  if (context.source[abstractSample().episode()]) {
    encounter {
      reference = "Encounter/" + context.source[abstractSample().episode().id()]
    }
  }

  receivedTime {
    date = context.source[abstractSample().samplingDate().date()]
  }

  final def ucum = context.conceptMaps.builtin("centraxx_ucum")
  collection {
    collectedDateTime {
      date = context.source[abstractSample().samplingDate().date()]
      quantity {
        value = context.source[abstractSample().initialAmount().amount()] as Number
        unit = ucum.translate(context.source[abstractSample().initialAmount().unit()] as String)?.code
        system = "http://unitsofmeasure.org"
      }
    }
  }

  container {
    if (context.source[abstractSample().receptable()]) {
      identifier {
        value = context.source[abstractSample().receptable().code()]
        system = "urn:centraxx"
      }

      capacity {
        value = context.source[abstractSample().receptable().size()]
        unit = ucum.translate(context.source[abstractSample().restAmount().unit()] as String)?.code
        system = "http://unitsofmeasure.org"
      }
    }

    specimenQuantity {
      value = context.source[abstractSample().restAmount().amount()] as Number
      unit = ucum.translate(context.source[abstractSample().restAmount().unit()] as String)?.code
      system = "http://unitsofmeasure.org"
    }
  }

//  if (context.source["organisationUnit"]) {
//    extension {
//      url = "https://fhir.bbmri.de/StructureDefinition/Custodian"
//      valueReference {
//        reference = "Organization/" + context.source["organisationUnit.id"]
//      }
//    }
//  }

  if (context.source[abstractSample().diagnosis()]) {
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
  final def temp = ctx.source[abstractSample().sampleLocation().temperature() as String]
  if (null != temp) {
    switch (temp) {
      case { it >= 2.0 && it <= 10 }: return "temperature2to10"
      case { it <= -18.0 && it >= -35.0 }: return "temperature-18to-35"
      case { it <= -60.0 && it >= -85.0 }: return "temperature-60to-85"
    }
  }

  final def sprec = ctx.source[abstractSample().receptable().sprecCode() as String]
  if (null != sprec) {
    switch (sprec) {
      case ['C', 'F', 'O', 'Q']:
        return "temperatureLN"
      case ['A', 'D', 'J', 'L', 'N', 'O', 'S']:
        return "temperature-60to-85"
      case ['B', 'H', 'K', 'M', 'T']:
        return "temperature-18to-35"
      default:
        return "temperatureOther"
    }
  }

  return null
}

static String sprecToBbmriSampleType(final String sprecCode) {
  if (sprecCode == null) {
    return null
  } else if (sprecCode == "ASC") { //Ascites fluid
    return "ascites"
  } else if (["AMN", //Amniotic fluid
              "BAL", //Bronchoalveolar lavage
              "BMK", //Breast milk
              "BUC", //Buccal cells
              "CEN", //Fresh cells from non blood specimen type
              "CLN", //Cells from non blood specimen type(eg disrupted tissue), viable
              "CRD", //Cord blood
              "NAS", //Nasal washing
              "PEL", //Ficoll mononuclear cells, non viable
              "PEN", //Cells from non blood specimen type (eg disrupted tissue), non viable
              "PFL", //Pleural fluid
              "RBC", //Red blood cells
              "SEM", //Semen
              "SPT", //Sputum
              "SYN", //Synovial fluid
              "TER"  // Tears
  ].contains(sprecCode)) {
    return "liquid-other"
  } else if (sprecCode == "BLD") { //Blood (whole)
    return "whole-blood"
  } else if (sprecCode == "BMA") { //Bone marrow aspirate
    return "bone-marrow"
  } else if (["BUF", //Unficolled buffy coat, viable
              "BFF" //Unficolled buffy coat, non-viable
  ].contains(sprecCode)) {
    return "buffy-coat"
  } else if (sprecCode == "CEL") {// Ficoll mononuclear cells viable
    return "peripheral-blood-cells-vital"
  } else if (sprecCode == "CSF") {//Cerebrospinal fluid
    return "csf-liquor"
  } else if (sprecCode == "DWB") {//Dried whole blood (e.g. Guthrie cards)
    return "dried-whole-blood"
  } else if (["PL1", //Plasma, single spun
              "PL2" //Plasma, double spun
  ].contains(sprecCode)) {
    return "blood-plasma"
  } else if (sprecCode == "SAL") {//Saliva
    return "saliva"
  } else if (sprecCode == "SER") {//Serum
    return "blood-serum"
  } else if (sprecCode == "STL") {//Stool
    return "stool-faeces"
  } else if (["U24", //24 h urine
              "URN", //Urine, random ("spot")
              "URM", //Urine, first morning
              "URT"  //Urine, timed
  ].contains(sprecCode)) {
    return "urine"
  } else if (sprecCode == "ZZZ") {//other
    return "derivative-other"
  } else if (["BON", //Bone
              "FNA", //Cells from fine needle aspirate
              "HAR", //Hair
              "LCM", //Cells from laser capture microdissected tissue
              "NAL", //Nails
              "PLC", //Placenta
              "TIS", //Solid tissue
              "TCM", //Cells from disrupted tissue
              "TTH"  //Teeth
  ].contains(sprecCode)) {
    return "tissue-other"
  }
  return null
}

static String sampleKindToBbmriSampleType(final String sampleKind) {
  if (sampleKind == null) {
    return null
  } else if (sampleKind == "TISSUE") {
    return "tissue-other"
  } else if (sampleKind == "LIQUID") {
    return "liquid-other"
  }
  return "derivative-other"
}