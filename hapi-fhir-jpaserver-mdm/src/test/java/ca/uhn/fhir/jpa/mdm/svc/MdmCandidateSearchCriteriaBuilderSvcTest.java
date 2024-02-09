package ca.uhn.fhir.jpa.mdm.svc;

import ca.uhn.fhir.jpa.mdm.BaseMdmR4Test;
import ca.uhn.fhir.jpa.mdm.svc.candidate.MdmCandidateSearchCriteriaBuilderSvc;
import ca.uhn.fhir.mdm.rules.json.MdmResourceSearchParamJson;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class MdmCandidateSearchCriteriaBuilderSvcTest extends BaseMdmR4Test {
	@Autowired
	MdmCandidateSearchCriteriaBuilderSvc myMdmCandidateSearchCriteriaBuilderSvc;

	@Test
	public void testEmptyCase() {
		Patient patient = new Patient();
		MdmResourceSearchParamJson searchParamJson = new MdmResourceSearchParamJson();
		searchParamJson.addSearchParam("family");
		Optional<String> result = myMdmCandidateSearchCriteriaBuilderSvc.buildResourceQueryString("Patient", patient, Collections.emptyList(), searchParamJson);
		assertThat(result.isPresent()).isFalse();
	}

	@Test
	public void testSimpleCase() {
		Patient patient = new Patient();
		patient.addName().setFamily("Fernandez");
		MdmResourceSearchParamJson searchParamJson = new MdmResourceSearchParamJson();
		searchParamJson.addSearchParam("family");
		Optional<String> result = myMdmCandidateSearchCriteriaBuilderSvc.buildResourceQueryString("Patient", patient, Collections.emptyList(), searchParamJson);
		assertThat(result).isPresent();
		assertThat(result).contains("Patient?family=Fernandez");
	}

	@Test
	public void testComplexCase() {
		Patient patient = new Patient();
		HumanName humanName = patient.addName();
		humanName.addGiven("Jose");
		humanName.addGiven("Martin");
		humanName.setFamily("Fernandez");
		MdmResourceSearchParamJson searchParamJson = new MdmResourceSearchParamJson();
		searchParamJson.addSearchParam("given");
		searchParamJson.addSearchParam("family");
		Optional<String> result = myMdmCandidateSearchCriteriaBuilderSvc.buildResourceQueryString("Patient", patient, Collections.emptyList(), searchParamJson);
		assertThat(result).isPresent();
		assertThat(result.get())
			.satisfiesAnyOf(
				arg -> assertThat(arg).isEqualTo("Patient?given=Jose,Martin&family=Fernandez"),
				arg -> assertThat(arg).isEqualTo("Patient?given=Martin,Jose&family=Fernandez")
			);
	}

	@Test
	public void testIdentifier() {
		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:oid:1.2.36.146.595.217.0.1").setValue("12345");
		MdmResourceSearchParamJson searchParamJson = new MdmResourceSearchParamJson();
		searchParamJson.addSearchParam("identifier");
		Optional<String> result = myMdmCandidateSearchCriteriaBuilderSvc.buildResourceQueryString("Patient", patient, Collections.emptyList(), searchParamJson);
		assertThat(result).isPresent();
		assertThat("Patient?identifier=urn%3Aoid%3A1.2.36.146.595.217.0.1%7C12345").isEqualTo(result.get());
	}

	@Test
	public void testIdentifierSpaceIsEscaped() {
		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:oid:1.2.36.146.595.217.0.1").setValue("abc def");
		MdmResourceSearchParamJson searchParamJson = new MdmResourceSearchParamJson();
		searchParamJson.addSearchParam("identifier");
		Optional<String> result = myMdmCandidateSearchCriteriaBuilderSvc.buildResourceQueryString("Patient", patient, Collections.emptyList(), searchParamJson);
		assertThat(result).isPresent();
		assertThat(result).contains("Patient?identifier=urn%3Aoid%3A1.2.36.146.595.217.0.1%7Cabc%20def");
	}

	@Test
	public void testOmittingCandidateSearchParamsIsAllowed() {
		Patient patient = new Patient();
		Optional<String> result = myMdmCandidateSearchCriteriaBuilderSvc.buildResourceQueryString("Patient", patient, Collections.emptyList(), null);
		assertThat(result.isPresent()).isEqualTo(true);
		assertThat(result).contains("Patient?");
	}

	@Test
	public void testEmptyCandidateSearchParamsWorksInConjunctionWithFilterParams() {
		Patient patient = new Patient();
		List<String> filterParams = Collections.singletonList("active=true");
		Optional<String> result = myMdmCandidateSearchCriteriaBuilderSvc.buildResourceQueryString("Patient", patient, filterParams, null);
		assertThat(result.isPresent()).isEqualTo(true);
		assertThat(result).contains("Patient?active=true");
	}
}
