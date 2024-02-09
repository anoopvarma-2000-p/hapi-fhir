package org.hl7.fhir.r4b.model;

import ca.uhn.fhir.context.FhirContext;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class ModelR4BTest {

	private static FhirContext ourCtx = FhirContext.forR4BCached();

	@Test
	public void testbase64BinaryName() {
		assertThat(ourCtx.getElementDefinition("base64binary").getName()).isEqualTo("base64Binary");
		assertThat(ourCtx.getElementDefinition("base64Binary").getName()).isEqualTo("base64Binary");
	}

	@Test
	public void testInstantPrecision() {
		new InstantType("2019-01-01T00:00:00Z");
		new InstantType("2019-01-01T00:00:00.0Z");
		new InstantType("2019-01-01T00:00:00.000Z");
		try {
			new InstantType("2019-01-01T00:00Z");
			fail("");		} catch (IllegalArgumentException e) {
			// good
		}
	}


	@Test
	public void testCompartmentsPopulated() {
		Set<String> compartments = ourCtx
			.getResourceDefinition("Observation")
			.getSearchParam("performer")
			.getProvidesMembershipInCompartments();
		assertThat(compartments).as(compartments.toString()).containsExactlyInAnyOrder("Practitioner", "Patient", "RelatedPerson");
	}


}
