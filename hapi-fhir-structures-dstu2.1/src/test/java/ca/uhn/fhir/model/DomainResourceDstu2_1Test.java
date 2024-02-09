package ca.uhn.fhir.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.hl7.fhir.dstu2016may.model.DomainResource;
import org.hl7.fhir.dstu2016may.model.Narrative;
import org.hl7.fhir.dstu2016may.model.Patient;
import org.junit.jupiter.api.Test;

public class DomainResourceDstu2_1Test {
    /**
     * Ensuring that Ensuring that fields defined in {@link org.hl7.fhir.dstu2016may.model.DomainResource} and {@link org.hl7.fhir.dstu2016may.model.Resource}
     * get copied when the {@link org.hl7.fhir.dstu2016may.model.DomainResource#copy()} method is called
     */
    @Test
    public void testPatientCopy() {
       Patient p1 = new Patient();
       p1.setId("1001");
       p1.setText(new Narrative().setStatus(Narrative.NarrativeStatus.ADDITIONAL));

       Patient copiedPatient = p1.copy();
       String copiedPatientID = copiedPatient.getIdElement().getIdPart();
       Narrative.NarrativeStatus copiedPatientTextStatus = copiedPatient.getText().getStatus();

			assertThat(copiedPatient instanceof DomainResource).isTrue(); // Just making sure this assumption still holds up, otherwise this test isn't very useful
			assertThat(copiedPatientID).isEqualTo("1001");
			assertThat(copiedPatientTextStatus).isEqualTo(new Narrative().setStatus(Narrative.NarrativeStatus.ADDITIONAL).getStatus());
    }
}
