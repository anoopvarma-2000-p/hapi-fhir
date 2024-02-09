package ca.uhn.fhir.model;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.lang3.SerializationUtils;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelSerializationDstu3Test {

	private static FhirContext ourCtx = FhirContext.forDstu3();

	/**
	 * Verify that MaritalStatusCodeEnum (and, by extension, BoundCodeableConcepts in general) are serializable. Author: Nick Peterson (nrpeterson@gmail.com)
	 */
	@Test
	public void testBoundCodeableConceptSerialization() {
		AdministrativeGender maritalStatus = AdministrativeGender.MALE;
		byte[] bytes = SerializationUtils.serialize(maritalStatus);
		assertThat(bytes.length > 0).isTrue();

		AdministrativeGender deserialized = SerializationUtils.deserialize(bytes);
		assertThat(deserialized).isEqualTo(AdministrativeGender.MALE);
	}

	@AfterAll
	public static void afterClassClearContext() {
		TestUtil.randomizeLocaleAndTimezone();
	}


	@Test
	public void testBoundCodeSerialization() {
		Patient p = new Patient();
		p.setGender(AdministrativeGender.MALE);

		Patient out = testIsSerializable(p);

		/*
		 * Make sure the binder still works for Code
		 */
		assertThat(out.getGender()).isEqualTo(AdministrativeGender.MALE);
		out.getGenderElement().setValueAsString("female");
		assertThat(out.getGender()).isEqualTo(AdministrativeGender.FEMALE);

	}

	@SuppressWarnings("unchecked")
	private <T extends IBaseResource> T testIsSerializable(T theObject) {
		byte[] bytes = SerializationUtils.serialize(theObject);
		assertThat(bytes.length > 0).isTrue();

		IBaseResource obj = SerializationUtils.deserialize(bytes);
		assertThat(obj != null).isTrue();

		IParser p = ourCtx.newXmlParser().setPrettyPrint(true);
		assertThat(p.encodeResourceToString(obj)).isEqualTo(p.encodeResourceToString(theObject));

		return (T) obj;
	}

	/**
	 * Contributed by Travis from iSalus
	 */
	@Test
	public void testSerialization2() {
		Patient patient = new Patient();
		patient.addName(new HumanName().addGiven("George").setFamily("Washington"));
		patient.addName(new HumanName().addGiven("George2").setFamily("Washington2"));
		patient.addAddress(new Address().addLine("line 1").addLine("line 2").setCity("city").setState("UT"));
		patient.addAddress(new Address().addLine("line 1b").addLine("line 2b").setCity("cityb").setState("UT"));
		patient.setBirthDate(new Date());

		testIsSerializable(patient);
	}

}
