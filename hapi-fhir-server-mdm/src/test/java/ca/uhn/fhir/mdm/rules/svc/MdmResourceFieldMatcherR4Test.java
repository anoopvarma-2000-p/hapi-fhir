package ca.uhn.fhir.mdm.rules.svc;

import ca.uhn.fhir.mdm.rules.json.MdmFieldMatchJson;
import ca.uhn.fhir.mdm.rules.json.MdmMatcherJson;
import ca.uhn.fhir.mdm.rules.json.MdmRulesJson;
import ca.uhn.fhir.mdm.rules.matcher.models.MatchTypeEnum;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class MdmResourceFieldMatcherR4Test extends BaseMdmRulesR4Test {
	protected MdmResourceFieldMatcher myComparator;
	private Patient myJohn;
	private Patient myJohny;

	@Override
	@BeforeEach
	public void before() {
		super.before();

		myComparator = new MdmResourceFieldMatcher(
			ourFhirContext,
			myIMatcherFactory,
			myGivenNameMatchField,
			myMdmRulesJson
		);
		myJohn = buildJohn();
		myJohny = buildJohny();
	}

	@Test
	public void testEmptyPath() {
		myMdmRulesJson = new MdmRulesJson();
		myMdmRulesJson.setMdmTypes(Arrays.asList(new String[]{"Patient"}));

		myGivenNameMatchField = new MdmFieldMatchJson()
			.setName("empty-given")
			.setResourceType("Patient")
			.setResourcePath("name.given")
			.setMatcher(new MdmMatcherJson().setAlgorithm(MatchTypeEnum.EMPTY_FIELD));
		myComparator = new MdmResourceFieldMatcher(
			ourFhirContext,
			myIMatcherFactory,
			myGivenNameMatchField,
			myMdmRulesJson
		);

		assertThat(myComparator.match(myJohn, myJohny).match).isFalse();

		myJohn.getName().clear();
		myJohny.getName().clear();

		assertThat(myComparator.match(myJohn, myJohny).match).isTrue();

		myJohn = buildJohn();
		myJohny.getName().clear();
		assertThat(myComparator.match(myJohn, myJohny).match).isFalse();

		myJohn.getName().clear();
		myJohny = buildJohny();
		assertThat(myComparator.match(myJohn, myJohny).match).isFalse();
	}

	@Test
	public void testSimplePatient() {
		Patient patient = new Patient();
		patient.setActive(true);

		assertThat(myComparator.match(patient, myJohny).match).isFalse();
	}

	@Test
	public void testBadType() {
		Encounter encounter = new Encounter();
		encounter.setId("Encounter/1");

		try {
			myComparator.match(encounter, myJohny);
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("Expecting resource type Patient got resource type Encounter");
		}
		try {
			myComparator.match(myJohn, encounter);
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("Expecting resource type Patient got resource type Encounter");
		}
	}

	// TODO - what is this supposed to test?
	// it relies on matcher being null (is this a reasonable assumption?)
	// and falls through to similarity check
	@Test
	public void testMatch() {
		assertThat(myComparator.match(myJohn, myJohny).match).isTrue();
	}
}
