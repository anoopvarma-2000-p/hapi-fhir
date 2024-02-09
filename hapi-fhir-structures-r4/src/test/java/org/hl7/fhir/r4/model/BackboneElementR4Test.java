package org.hl7.fhir.r4.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.hl7.fhir.r4.model.Patient.PatientCommunicationComponent;
import org.junit.jupiter.api.Test;

public class BackboneElementR4Test {
    /**
     * Ensuring that IDs of subtypes of BackboneElement get copied when
     * the {@link BackboneElement#copy()} method is called
     */
    @Test
    public void testPatientCommunicationComponentCopy() {
        PatientCommunicationComponent pcc1 = new PatientCommunicationComponent();
        pcc1.setId("1001");

        PatientCommunicationComponent copiedPcc = pcc1.copy();
        String copiedPccID = copiedPcc.getId();

			assertThat(copiedPcc instanceof BackboneElement).isTrue(); // Just making sure this assumption still holds up, otherwise this test isn't very useful
			assertThat(copiedPccID).isEqualTo("1001");
    }
}
